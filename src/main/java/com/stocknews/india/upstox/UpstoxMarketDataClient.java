package com.stocknews.india.upstox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocknews.config.UpstoxProperties;
import com.stocknews.india.upstox.UpstoxInstrumentRegistry.Instrument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Live quotes and historical candles for NSE/BSE equities from the Upstox API. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpstoxMarketDataClient {
    /** Upstox allows up to 500 instrument keys per market-quote call. */
    static final int MAX_KEYS_PER_CALL = 500;
    static final Set<String> EXCHANGES = Set.of("NSE", "BSE");
    static final Set<String> UNITS = Set.of("minutes", "hours", "days", "weeks", "months");
    private static final String TOKEN_HELP = "Generate a free Analytics Token at https://account.upstox.com/developer/apps "
            + "(Analytics tab) and set UPSTOX_ACCESS_TOKEN";

    private final RestClient restClient;
    private final UpstoxProperties upstoxProperties;
    private final UpstoxInstrumentRegistry upstoxInstrumentRegistry;
    private final ObjectMapper objectMapper;

    public record UpstoxQuote(String symbol, String exchange, String name, String isin, String instrumentKey,
                              BigDecimal lastPrice, BigDecimal open, BigDecimal high, BigDecimal low,
                              BigDecimal close, BigDecimal netChange, Long volume, BigDecimal averagePrice,
                              BigDecimal lowerCircuitLimit, BigDecimal upperCircuitLimit, String timestamp) {
    }

    public record UpstoxCandle(String timestamp, BigDecimal open, BigDecimal high, BigDecimal low,
                               BigDecimal close, Long volume, Long openInterest) {
    }

    /**
     * Full market quotes in request order. Symbols Upstox does not list are logged and skipped so the
     * other symbols still return.
     */
    public List<UpstoxQuote> fetchQuotes(List<String> symbols, String exchange) {
        String venue = exchange(exchange);
        Map<String, Instrument> byKey = new LinkedHashMap<>();
        for (String symbol : symbols) {
            upstoxInstrumentRegistry.find(venue, symbol).ifPresentOrElse(
                    instrument -> byKey.put(instrument.instrumentKey(), instrument),
                    () -> log.error("Skipping Upstox quote for {}: not an {} equity symbol", symbol, venue));
        }
        if (byKey.isEmpty()) {
            return List.of();
        }

        Map<String, UpstoxQuote> quotesByKey = new HashMap<>();
        List<String> keys = new ArrayList<>(byKey.keySet());
        for (int start = 0; start < keys.size(); start += MAX_KEYS_PER_CALL) {
            List<String> chunk = keys.subList(start, Math.min(start + MAX_KEYS_PER_CALL, keys.size()));
            URI uri = UriComponentsBuilder.fromUriString(upstoxProperties.getBaseUrl())
                    .path("/v2/market-quote/quotes")
                    .queryParam("instrument_key", String.join(",", chunk))
                    .encode()
                    .build()
                    .toUri();
            JsonNode data = get(uri, "market quote").path("data");
            // Response keys look like "NSE_EQ:RELIANCE"; match on instrument_token (NSE_EQ|INE002A01018).
            data.forEach(item -> {
                Instrument instrument = byKey.get(item.path("instrument_token").asText());
                if (instrument != null) {
                    quotesByKey.put(instrument.instrumentKey(), toQuote(instrument, item));
                }
            });
        }

        List<UpstoxQuote> result = new ArrayList<>();
        byKey.forEach((key, instrument) -> {
            UpstoxQuote quote = quotesByKey.get(key);
            if (quote == null) {
                log.error("Skipping Upstox quote for {}: no data returned", instrument.tradingSymbol());
            } else {
                result.add(quote);
            }
        });
        return result;
    }

    /**
     * Historical OHLCV candles. {@code unit}: minutes (interval 1-300), hours (1-5), days, weeks,
     * months (interval 1). Minute data starts January 2022; daily data starts January 2000.
     */
    public List<UpstoxCandle> fetchCandles(String symbol, String exchange, String unit, int interval,
                                           LocalDate from, LocalDate to) {
        String venue = exchange(exchange);
        String candleUnit = unit.toLowerCase(Locale.ROOT);
        if (!UNITS.contains(candleUnit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unit must be one of " + UNITS);
        }
        if (from != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        Instrument instrument = upstoxInstrumentRegistry.find(venue, symbol).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, symbol + " is not an " + venue + " equity symbol"));

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(upstoxProperties.getBaseUrl())
                .pathSegment("v3", "historical-candle", instrument.instrumentKey(), candleUnit,
                        String.valueOf(interval), to.toString());
        if (from != null) {
            builder.pathSegment(from.toString());
        }
        JsonNode candles = get(builder.encode().build().toUri(), "historical candles").path("data").path("candles");

        List<UpstoxCandle> result = new ArrayList<>();
        for (JsonNode candle : candles) {
            // Order: timestamp, open, high, low, close, volume, open interest.
            result.add(new UpstoxCandle(candle.path(0).asText(), decimal(candle.path(1)), decimal(candle.path(2)),
                    decimal(candle.path(3)), decimal(candle.path(4)), longValue(candle.path(5)),
                    longValue(candle.path(6))));
        }
        return result;
    }

    private JsonNode get(URI uri, String what) {
        String token = upstoxProperties.getAccessToken();
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Upstox access token is not configured. " + TOKEN_HELP);
        }
        try {
            String body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.error("Upstox {} request failed with HTTP {}: {}", what, status, exception.getResponseBodyAsString());
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Upstox rejected the access token (expired or invalid). " + TOKEN_HELP, exception);
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Upstox rate limit reached", exception);
            }
            if (status == HttpStatus.BAD_REQUEST.value()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Upstox rejected the request: " + exception.getResponseBodyAsString(), exception);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstox " + what + " request failed", exception);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            log.error("Upstox {} request failed: {}", what, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstox " + what + " request failed", exception);
        }
    }

    private static UpstoxQuote toQuote(Instrument instrument, JsonNode item) {
        JsonNode ohlc = item.path("ohlc");
        return new UpstoxQuote(instrument.tradingSymbol(), instrument.exchange(), instrument.name(), instrument.isin(),
                instrument.instrumentKey(), decimal(item.path("last_price")), decimal(ohlc.path("open")),
                decimal(ohlc.path("high")), decimal(ohlc.path("low")), decimal(ohlc.path("close")),
                decimal(item.path("net_change")), longValue(item.path("volume")), decimal(item.path("average_price")),
                decimal(item.path("lower_circuit_limit")), decimal(item.path("upper_circuit_limit")),
                item.path("timestamp").asText(null));
    }

    private static String exchange(String exchange) {
        String venue = exchange == null ? "NSE" : exchange.trim().toUpperCase(Locale.ROOT);
        if (!EXCHANGES.contains(venue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exchange must be NSE or BSE");
        }
        return venue;
    }

    private static BigDecimal decimal(JsonNode value) {
        return value.isNumber() ? value.decimalValue() : null;
    }

    private static Long longValue(JsonNode value) {
        return value.isNumber() ? value.longValue() : null;
    }
}
