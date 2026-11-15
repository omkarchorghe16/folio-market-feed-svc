package com.stocknews.india.tapetide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.stocknews.config.TapetideProperties;
import com.stocknews.india.IndiaSymbols;
import com.stocknews.model.AuditedEntity;
import com.stocknews.model.TapetideStockDataset;
import com.stocknews.model.TapetideStockQuote;
import com.stocknews.repository.TapetideStockDatasetRepository;
import com.stocknews.repository.TapetideStockQuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * NSE/BSE data from Tapetide, saved to PostgreSQL with created/updated dates.
 *
 * <p>Rows refreshed recently are served from the database to save the free plan's 50 calls/day.
 * Nothing here throws for a bad symbol or a Tapetide failure: the problem is logged with details,
 * the last saved row (if any) is returned instead, and other symbols carry on.
 */
@Service
@Slf4j
public class TapetideStockService {
    /** Tapetide's get_batch_quotes accepts at most 20 symbols per call. */
    static final int MAX_BATCH = 20;

    private final TapetideClient client;
    private final TapetideStockQuoteRepository quoteRepository;
    private final TapetideStockDatasetRepository datasetRepository;
    private final TapetideProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public TapetideStockService(TapetideClient client, TapetideStockQuoteRepository quoteRepository,
                                TapetideStockDatasetRepository datasetRepository, TapetideProperties properties,
                                ObjectMapper objectMapper) {
        this(client, quoteRepository, datasetRepository, properties, objectMapper, Clock.systemDefaultZone());
    }

    TapetideStockService(TapetideClient client, TapetideStockQuoteRepository quoteRepository,
                         TapetideStockDatasetRepository datasetRepository, TapetideProperties properties,
                         ObjectMapper objectMapper, Clock clock) {
        this.client = client;
        this.quoteRepository = quoteRepository;
        this.datasetRepository = datasetRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Symbol search is not stock data, so it is not saved. Returns an empty array on any problem. */
    public JsonNode search(String query, Integer limit) {
        if (query == null || query.isBlank()) {
            log.error("Tapetide search skipped: query is empty");
            return objectMapper.createArrayNode();
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", query.trim());
        if (limit != null) {
            args.put("limit", limit);
        }
        try {
            return client.callTool("search_stocks", args);
        } catch (ResponseStatusException exception) {
            log.error("Tapetide search failed for query '{}': status={}, reason={}",
                    query, exception.getStatusCode().value(), exception.getReason());
            return objectMapper.createArrayNode();
        }
    }

    // ---------------------------------------------------------------- quotes

    /** Latest quote (price, P/E, P/B, 52-week range). Empty if the symbol is invalid and nothing is saved. */
    public Optional<TapetideStockQuote> quote(String rawSymbol) {
        Optional<String> symbol = IndiaSymbols.normalize(rawSymbol, "Tapetide quote");
        if (symbol.isEmpty()) {
            return Optional.empty();
        }
        Optional<TapetideStockQuote> saved = quoteRepository.findBySymbol(symbol.get());
        if (saved.isPresent() && isFresh(saved.get(), properties.getQuoteRefreshMinutes())) {
            log.info("Tapetide quote for {} served from PostgreSQL (updated {})", symbol.get(), saved.get().getUpdatedDate());
            return saved;
        }
        try {
            JsonNode data = client.callTool("get_stock_quote", Map.of("symbol", symbol.get())).path("data");
            if (!isFound(data)) {
                log.error("Tapetide quote skipped for {}: symbol not found (requested={}, response={})",
                        symbol.get(), rawSymbol, abbreviate(data));
                return saved;
            }
            return Optional.of(upsertQuote(symbol.get(), data, saved.orElse(null)));
        } catch (ResponseStatusException exception) {
            logSkip("quote", symbol.get(), exception);
            return saved;
        }
    }

    /**
     * Quotes for many symbols, 20 per Tapetide call, in request order. Invalid or unknown symbols are
     * logged and left out; a failed batch falls back to saved rows; a rate limit stops further calls.
     */
    public List<TapetideStockQuote> quotes(List<String> rawSymbols) {
        List<String> symbols = rawSymbols == null ? List.of() : rawSymbols.stream()
                .map(raw -> IndiaSymbols.normalize(raw, "Tapetide quotes"))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        if (symbols.isEmpty()) {
            log.error("Tapetide quotes skipped: no valid symbols in {}", rawSymbols);
            return List.of();
        }

        Map<String, TapetideStockQuote> bySymbol = quoteRepository.findAllBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(TapetideStockQuote::getSymbol, quote -> quote, (a, b) -> a, HashMap::new));
        List<String> toFetch = symbols.stream()
                .filter(symbol -> !bySymbol.containsKey(symbol)
                        || !isFresh(bySymbol.get(symbol), properties.getQuoteRefreshMinutes()))
                .toList();
        log.info("Tapetide quotes: {} requested, {} served from PostgreSQL, {} to fetch",
                symbols.size(), symbols.size() - toFetch.size(), toFetch.size());

        for (int start = 0; start < toFetch.size(); start += MAX_BATCH) {
            List<String> chunk = toFetch.subList(start, Math.min(start + MAX_BATCH, toFetch.size()));
            try {
                JsonNode items = client.callTool("get_batch_quotes", Map.of("symbols", chunk)).path("data");
                Map<String, JsonNode> returned = new HashMap<>();
                for (JsonNode item : items) {
                    String key = item.path("requested").asText(item.path("symbol").asText("")).toUpperCase();
                    returned.put(key, item);
                }
                for (String symbol : chunk) {
                    JsonNode item = returned.get(symbol);
                    if (!isFound(item)) {
                        log.error("Tapetide quote skipped for {}: symbol not found in batch response ({})",
                                symbol, abbreviate(item));
                        continue;
                    }
                    bySymbol.put(symbol, upsertQuote(symbol, item, bySymbol.get(symbol)));
                }
            } catch (ResponseStatusException exception) {
                log.error("Tapetide quotes skipped for {}: status={}, reason={}; returning saved rows if any",
                        chunk, exception.getStatusCode().value(), exception.getReason());
                if (stopsFurtherCalls(exception)) {
                    break;
                }
            }
        }
        return symbols.stream().map(bySymbol::get).filter(java.util.Objects::nonNull).toList();
    }

    private TapetideStockQuote upsertQuote(String symbol, JsonNode data, TapetideStockQuote existing) {
        TapetideStockQuote quote = existing != null ? existing : new TapetideStockQuote();
        quote.setSymbol(symbol);
        // Batch quotes omit P/E, P/B and 52-week values; only overwrite fields present in the response.
        text(data, "name", quote::setName);
        decimal(data, "price", quote::setPrice);
        decimal(data, "change", quote::setChange);
        decimal(data, "change_pct", quote::setChangePct);
        decimal(data, "open", quote::setOpen);
        decimal(data, "high", quote::setHigh);
        decimal(data, "low", quote::setLow);
        decimal(data, "prev_close", quote::setPrevClose);
        if (data.path("volume").isNumber()) {
            quote.setVolume(data.path("volume").longValue());
        }
        decimal(data, "pe_ttm", quote::setPeTtm);
        decimal(data, "pb", quote::setPb);
        decimal(data, "high_52w", quote::setHigh52w);
        decimal(data, "low_52w", quote::setLow52w);
        text(data, "last_trade_date", quote::setLastTradeDate);
        text(data, "price_basis", quote::setPriceBasis);
        text(data, "updated_at", quote::setProviderUpdatedAt);
        quote.setRawPayload(data.toString());
        quote.touch(LocalDateTime.now(clock));
        TapetideStockQuote saved = quoteRepository.saveAndFlush(quote);
        log.info("Saved Tapetide quote for {} to PostgreSQL ({})", symbol, existing == null ? "created" : "updated");
        return saved;
    }

    // ------------------------------------------------------------- datasets

    /** Company overview with fundamentals; {@code include} may contain technicals, ratings, peers. */
    public Optional<TapetideStockDataset> profile(String symbol, List<String> include) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (include != null && !include.isEmpty()) {
            args.put("include", include.stream().map(String::trim).map(String::toLowerCase).sorted().toList());
        }
        return dataset("profile", "get_company_profile", symbol, args);
    }

    /** {@code section}: quarterly, profit_loss, balance_sheet, cash_flow, ratios (null = all). */
    public Optional<TapetideStockDataset> financials(String symbol, String section, Integer periods) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (section != null && !section.isBlank()) {
            args.put("section", section.trim().toLowerCase());
        }
        if (periods != null) {
            args.put("periods", periods);
        }
        return dataset("financials", "get_financials", symbol, args);
    }

    /** {@code type}: quarterly or annual (null = all). */
    public Optional<TapetideStockDataset> shareholding(String symbol, String type) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (type != null && !type.isBlank()) {
            args.put("type", type.trim().toLowerCase());
        }
        return dataset("shareholding", "get_shareholding", symbol, args);
    }

    /** Analyst estimates (EPS, revenue, EBITDA, net income, ROA, ROE, price target) against actuals. */
    public Optional<TapetideStockDataset> forecasts(String symbol) {
        return dataset("forecasts", "get_forecasts", symbol, Map.of());
    }

    /** Daily or weekly OHLCV with delivery %; {@code days} max 2000. */
    public Optional<TapetideStockDataset> priceHistory(String symbol, Integer days, String interval) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (days != null) {
            args.put("days", days);
        }
        if (interval != null && !interval.isBlank()) {
            args.put("interval", interval.trim().toLowerCase());
        }
        return dataset("history", "get_price_history", symbol, args);
    }

    private Optional<TapetideStockDataset> dataset(String dataset, String tool, String rawSymbol,
                                                   Map<String, Object> options) {
        Optional<String> normalized = IndiaSymbols.normalize(rawSymbol, "Tapetide " + dataset);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String symbol = normalized.get();
        String requestParams = options.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .collect(Collectors.joining(";"));

        Optional<TapetideStockDataset> saved =
                datasetRepository.findBySymbolAndDatasetAndRequestParams(symbol, dataset, requestParams);
        if (saved.isPresent() && isFresh(saved.get(), properties.getDataRefreshMinutes())) {
            log.info("Tapetide {} for {} [{}] served from PostgreSQL (updated {})",
                    dataset, symbol, requestParams, saved.get().getUpdatedDate());
            return saved;
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("symbol", symbol);
        args.putAll(options);
        try {
            JsonNode result = client.callTool(tool, args);
            JsonNode data = result.path("data");
            if (!hasData(data)) {
                log.error("Tapetide {} skipped for {} [{}]: no data returned (requested={}, response={})",
                        dataset, symbol, requestParams, rawSymbol, abbreviate(result));
                return saved;
            }
            TapetideStockDataset row = saved.orElseGet(TapetideStockDataset::new);
            row.setSymbol(symbol);
            row.setDataset(dataset);
            row.setRequestParams(requestParams);
            row.setPayload(result.toString());
            row.touch(LocalDateTime.now(clock));
            TapetideStockDataset stored = datasetRepository.saveAndFlush(row);
            log.info("Saved Tapetide {} for {} [{}] to PostgreSQL ({})",
                    dataset, symbol, requestParams, saved.isEmpty() ? "created" : "updated");
            return Optional.of(stored);
        } catch (ResponseStatusException exception) {
            logSkip(dataset, symbol, exception);
            return saved;
        }
    }

    // --------------------------------------------------------------- helpers

    private boolean isFresh(AuditedEntity row, int maxAgeMinutes) {
        return maxAgeMinutes > 0 && row.getUpdatedDate() != null
                && row.getUpdatedDate().isAfter(LocalDateTime.now(clock).minusMinutes(maxAgeMinutes));
    }

    /** Tapetide marks unknown symbols with {@code "found": false}. */
    private static boolean isFound(JsonNode item) {
        return item != null && item.isObject() && !item.isEmpty() && item.path("found").asBoolean(true);
    }

    private static boolean hasData(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return false;
        }
        if (data.isObject()) {
            return !data.isEmpty() && data.path("found").asBoolean(true);
        }
        return !(data instanceof ArrayNode array) || !array.isEmpty();
    }

    private static boolean stopsFurtherCalls(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        return status == HttpStatus.TOO_MANY_REQUESTS.value() || status == HttpStatus.SERVICE_UNAVAILABLE.value();
    }

    private static void logSkip(String dataset, String symbol, ResponseStatusException exception) {
        log.error("Tapetide {} skipped for {}: status={}, reason={}; returning saved row if any",
                dataset, symbol, exception.getStatusCode().value(), exception.getReason());
    }

    private static void decimal(JsonNode data, String field, Consumer<BigDecimal> setter) {
        JsonNode value = data.path(field);
        if (value.isNumber()) {
            setter.accept(value.decimalValue());
        }
    }

    private static void text(JsonNode data, String field, Consumer<String> setter) {
        JsonNode value = data.path(field);
        if (value.isTextual() && !value.asText().isBlank()) {
            setter.accept(value.asText());
        }
    }

    private static String abbreviate(JsonNode node) {
        String text = node == null ? "null" : node.toString();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
