package com.stocknews.india.upstox;

import com.stocknews.india.IndiaSymbols;
import com.stocknews.india.upstox.UpstoxMarketDataClient.UpstoxCandle;
import com.stocknews.india.upstox.UpstoxMarketDataClient.UpstoxQuote;
import com.stocknews.model.UpstoxStockCandle;
import com.stocknews.model.UpstoxStockQuote;
import com.stocknews.repository.UpstoxStockCandleRepository;
import com.stocknews.repository.UpstoxStockQuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Upstox quotes and candles, always fetched live and upserted to PostgreSQL with created/updated dates.
 * Nothing here throws for a bad symbol, bad parameter or Upstox failure: the problem is logged with
 * details and the last saved data (if any) is returned instead.
 */
@Service
@Slf4j
public class UpstoxMarketDataService {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final UpstoxMarketDataClient client;
    private final UpstoxStockQuoteRepository quoteRepository;
    private final UpstoxStockCandleRepository candleRepository;
    private final Clock clock;

    @Autowired
    public UpstoxMarketDataService(UpstoxMarketDataClient client, UpstoxStockQuoteRepository quoteRepository,
                                   UpstoxStockCandleRepository candleRepository) {
        this(client, quoteRepository, candleRepository, Clock.systemDefaultZone());
    }

    UpstoxMarketDataService(UpstoxMarketDataClient client, UpstoxStockQuoteRepository quoteRepository,
                            UpstoxStockCandleRepository candleRepository, Clock clock) {
        this.client = client;
        this.quoteRepository = quoteRepository;
        this.candleRepository = candleRepository;
        this.clock = clock;
    }

    /** Live quotes in request order; symbols that cannot be quoted are logged and left out. */
    public List<UpstoxStockQuote> quotes(List<String> rawSymbols, String rawExchange) {
        Optional<String> exchange = exchange(rawExchange, "Upstox quotes");
        List<String> symbols = rawSymbols == null ? List.of() : rawSymbols.stream()
                .map(raw -> IndiaSymbols.normalize(raw, "Upstox quote"))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        if (exchange.isEmpty() || symbols.isEmpty()) {
            log.error("Upstox quotes skipped: no valid symbols/exchange (symbols={}, exchange={})", rawSymbols, rawExchange);
            return List.of();
        }

        Map<String, UpstoxStockQuote> saved = quoteRepository.findAllByExchangeAndSymbolIn(exchange.get(), symbols)
                .stream().collect(Collectors.toMap(UpstoxStockQuote::getSymbol, Function.identity(), (a, b) -> a, HashMap::new));
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            for (UpstoxQuote quote : client.quotes(symbols, exchange.get())) {
                UpstoxStockQuote row = saved.getOrDefault(quote.symbol(), new UpstoxStockQuote());
                boolean created = row.getId() == null;
                copy(quote, row);
                row.touch(now);
                saved.put(quote.symbol(), quoteRepository.saveAndFlush(row));
                log.info("Saved Upstox quote for {}:{} to PostgreSQL ({})",
                        exchange.get(), quote.symbol(), created ? "created" : "updated");
            }
        } catch (ResponseStatusException exception) {
            log.error("Upstox quotes failed for {} on {}: status={}, reason={}; returning saved rows if any",
                    symbols, exchange.get(), exception.getStatusCode().value(), exception.getReason());
        }
        return symbols.stream().map(saved::get).filter(Objects::nonNull).toList();
    }

    /**
     * Fetches candles and upserts them (re-fetching a candle updates it rather than duplicating it).
     * On any problem, logs it and returns the candles already saved for that range.
     *
     * @param fromDate {@code yyyy-MM-dd}; defaults to one month before {@code toDate}
     * @param toDate   {@code yyyy-MM-dd}; defaults to today in IST
     */
    public List<UpstoxStockCandle> candles(String rawSymbol, String rawExchange, String rawUnit, Integer rawInterval,
                                           String fromDate, String toDate) {
        Optional<String> symbol = IndiaSymbols.normalize(rawSymbol, "Upstox candles");
        Optional<String> exchange = exchange(rawExchange, "Upstox candles");
        String unit = rawUnit == null || rawUnit.isBlank() ? "days" : rawUnit.trim().toLowerCase(Locale.ROOT);
        int interval = rawInterval == null ? 1 : rawInterval;
        if (symbol.isEmpty() || exchange.isEmpty()) {
            return List.of();
        }
        LocalDate end;
        LocalDate start;
        try {
            end = isBlank(toDate) ? LocalDate.now(IST) : LocalDate.parse(toDate.trim());
            start = isBlank(fromDate) ? end.minusMonths(1) : LocalDate.parse(fromDate.trim());
        } catch (DateTimeParseException exception) {
            log.error("Upstox candles skipped for {}: dates must be yyyy-MM-dd (from='{}', to='{}')",
                    symbol.get(), fromDate, toDate);
            return List.of();
        }
        if (!UpstoxMarketDataClient.UNITS.contains(unit)) {
            log.error("Upstox candles skipped for {}: unit '{}' must be one of {}", symbol.get(), rawUnit,
                    UpstoxMarketDataClient.UNITS);
            return List.of();
        }
        if (start.isAfter(end)) {
            log.error("Upstox candles skipped for {}: from {} is after to {}", symbol.get(), start, end);
            return List.of();
        }

        OffsetDateTime rangeStart = start.atStartOfDay(IST).toOffsetDateTime();
        OffsetDateTime rangeEnd = end.plusDays(1).atStartOfDay(IST).toOffsetDateTime().minusNanos(1);
        List<UpstoxStockCandle> saved = candleRepository
                .findAllBySymbolAndExchangeAndUnitAndIntervalAndCandleTimeBetweenOrderByCandleTimeDesc(
                        symbol.get(), exchange.get(), unit, interval, rangeStart, rangeEnd);

        List<UpstoxCandle> fetched;
        try {
            fetched = client.candles(symbol.get(), exchange.get(), unit, interval, start, end);
        } catch (ResponseStatusException exception) {
            log.error("Upstox candles failed for {}:{} {} x{} {}..{}: status={}, reason={}; returning {} saved candles",
                    exchange.get(), symbol.get(), unit, interval, start, end, exception.getStatusCode().value(),
                    exception.getReason(), saved.size());
            return saved;
        }

        // Match on the instant: PostgreSQL returns candle_time in UTC while Upstox sends +05:30.
        Map<Instant, UpstoxStockCandle> byTime = saved.stream()
                .collect(Collectors.toMap(candle -> candle.getCandleTime().toInstant(), Function.identity(), (a, b) -> a));
        LocalDateTime now = LocalDateTime.now(clock);
        int created = 0;
        List<UpstoxStockCandle> rows = new ArrayList<>();
        for (UpstoxCandle candle : fetched) {
            OffsetDateTime time;
            try {
                time = OffsetDateTime.parse(candle.timestamp());
            } catch (DateTimeParseException | NullPointerException exception) {
                log.error("Skipping Upstox candle for {} with unreadable timestamp '{}'", symbol.get(), candle.timestamp());
                continue;
            }
            UpstoxStockCandle row = byTime.get(time.toInstant());
            if (row == null) {
                row = new UpstoxStockCandle();
                created++;
            }
            row.setSymbol(symbol.get());
            row.setExchange(exchange.get());
            row.setUnit(unit);
            row.setInterval(interval);
            row.setCandleTime(time);
            row.setOpen(candle.open());
            row.setHigh(candle.high());
            row.setLow(candle.low());
            row.setClose(candle.close());
            row.setVolume(candle.volume());
            row.setOpenInterest(candle.openInterest());
            row.touch(now);
            rows.add(row);
        }
        List<UpstoxStockCandle> stored = candleRepository.saveAllAndFlush(rows);
        log.info("Saved {} Upstox candles for {}:{} {} x{} to PostgreSQL ({} created, {} updated)",
                stored.size(), exchange.get(), symbol.get(), unit, interval, created, stored.size() - created);
        return stored;
    }

    private static void copy(UpstoxQuote quote, UpstoxStockQuote row) {
        row.setSymbol(quote.symbol());
        row.setExchange(quote.exchange());
        row.setName(quote.name());
        row.setIsin(quote.isin());
        row.setInstrumentKey(quote.instrumentKey());
        row.setLastPrice(quote.lastPrice());
        row.setOpen(quote.open());
        row.setHigh(quote.high());
        row.setLow(quote.low());
        row.setClose(quote.close());
        row.setNetChange(quote.netChange());
        row.setVolume(quote.volume());
        row.setAveragePrice(quote.averagePrice());
        row.setLowerCircuitLimit(quote.lowerCircuitLimit());
        row.setUpperCircuitLimit(quote.upperCircuitLimit());
        row.setQuoteTimestamp(quote.timestamp());
    }

    private static Optional<String> exchange(String raw, String context) {
        String exchange = raw == null || raw.isBlank() ? "NSE" : raw.trim().toUpperCase(Locale.ROOT);
        if (!UpstoxMarketDataClient.EXCHANGES.contains(exchange)) {
            log.error("{} skipped: exchange '{}' must be NSE or BSE", context, raw);
            return Optional.empty();
        }
        return Optional.of(exchange);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
