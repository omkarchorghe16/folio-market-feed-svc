package com.stocknews.india.upstox;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocknews.config.UpstoxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Maps trading symbols (RELIANCE) to Upstox instrument keys (NSE_EQ|INE002A01018) using Upstox's
 * public daily instrument files. Only cash-equity rows are kept; the list is reloaded once per day.
 */
@Component
@Slf4j
public class UpstoxInstrumentRegistry {
    public record Instrument(String tradingSymbol, String name, String exchange, String isin, String instrumentKey) {
    }

    private record Snapshot(LocalDate loadedOn, Map<String, Instrument> bySymbol) {
    }

    private final RestClient restClient;
    private final UpstoxProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public UpstoxInstrumentRegistry(RestClient restClient, UpstoxProperties properties, ObjectMapper objectMapper) {
        this(restClient, properties, objectMapper, Clock.system(ZoneId.of(properties.getTimeZone())));
    }

    UpstoxInstrumentRegistry(RestClient restClient, UpstoxProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Optional<Instrument> find(String exchange, String symbol) {
        return Optional.ofNullable(load(exchange).get(symbol.trim().toUpperCase(Locale.ROOT)));
    }

    private Map<String, Instrument> load(String exchange) {
        LocalDate today = LocalDate.now(clock);
        Snapshot current = snapshots.get(exchange);
        if (current != null && current.loadedOn().equals(today)) {
            return current.bySymbol();
        }
        synchronized (this) {
            current = snapshots.get(exchange);
            if (current != null && current.loadedOn().equals(today)) {
                return current.bySymbol();
            }
            try {
                Map<String, Instrument> loaded = download(exchange);
                snapshots.put(exchange, new Snapshot(today, loaded));
                log.info("Loaded {} Upstox {} equity instruments", loaded.size(), exchange);
                return loaded;
            } catch (RuntimeException | IOException exception) {
                if (current != null) {
                    log.error("Could not refresh Upstox {} instruments; using list from {}: {}",
                            exchange, current.loadedOn(), exception.getMessage());
                    return current.bySymbol();
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not load Upstox " + exchange + " instrument list", exception);
            }
        }
    }

    private Map<String, Instrument> download(String exchange) throws IOException {
        String url = properties.getInstrumentsUrl().replace("{exchange}", exchange);
        String equitySegment = exchange + "_EQ";
        try {
            return restClient.get().uri(url).exchange((request, response) -> {
                if (response.getStatusCode().isError()) {
                    throw new IOException("HTTP " + response.getStatusCode().value() + " from " + url);
                }
                return parse(response.getBody(), equitySegment);
            });
        } catch (RestClientException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    /** Streams the gzipped JSON array so the full file (including F&O rows) is never held in memory. */
    Map<String, Instrument> parse(InputStream gzipped, String equitySegment) throws IOException {
        Map<String, Instrument> bySymbol = new HashMap<>();
        try (InputStream in = new GZIPInputStream(gzipped);
             JsonParser parser = objectMapper.getFactory().createParser(in)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Upstox instrument file is not a JSON array");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode row = objectMapper.readTree(parser);
                if (!equitySegment.equals(row.path("segment").asText())) {
                    continue;
                }
                String symbol = row.path("trading_symbol").asText("").toUpperCase(Locale.ROOT);
                String key = row.path("instrument_key").asText(null);
                if (symbol.isEmpty() || key == null) {
                    continue;
                }
                bySymbol.putIfAbsent(symbol, new Instrument(symbol, row.path("name").asText(null),
                        row.path("exchange").asText(null), row.path("isin").asText(null), key));
            }
        }
        return bySymbol;
    }
}
