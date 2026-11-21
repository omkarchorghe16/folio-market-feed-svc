package com.stocknews.us.nasdaq;

import com.stocknews.config.NasdaqProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Keeps Nasdaq Trader's US symbol-directory files on disk and searches them for type-ahead dropdowns.
 * {@link #refreshSymbolLists()} replaces both files only after both downloads parse successfully, so a
 * failed or partial download never removes the previous lists. Searches read the stored files and keep
 * the parsed rows in memory until a file changes on disk.
 */
@Service
@Slf4j
public class NasdaqSymbolService {
    private static final int NO_MATCH = Integer.MAX_VALUE;

    public record SyncResult(int equities, int etfs, Instant syncedAt) {
    }

    private record Snapshot(Map<NasdaqSymbolFile, FileTime> modifiedTimes, List<NasdaqSymbol> symbols) {
    }

    private final NasdaqSymbolListClient nasdaqSymbolListClient;
    private final Path storageDirectory;
    private volatile Snapshot snapshot;

    public NasdaqSymbolService(NasdaqSymbolListClient nasdaqSymbolListClient, NasdaqProperties nasdaqProperties) {
        this.nasdaqSymbolListClient = nasdaqSymbolListClient;
        this.storageDirectory = Path.of(nasdaqProperties.getStorageDirectory());
    }

    /** Downloads both Nasdaq Trader files and atomically replaces the stored copies. */
    public synchronized SyncResult refreshSymbolLists() {
        Map<NasdaqSymbolFile, String> downloads = new EnumMap<>(NasdaqSymbolFile.class);
        Map<NasdaqSymbolFile, List<NasdaqSymbol>> parsed = new EnumMap<>(NasdaqSymbolFile.class);
        try {
            for (NasdaqSymbolFile file : NasdaqSymbolFile.values()) {
                String text = nasdaqSymbolListClient.fetchSymbolList(file);
                parsed.put(file, NasdaqSymbolFileParser.parse(text, file));
                downloads.put(file, text);
            }
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Nasdaq symbol sync failed; keeping the previously stored lists: {}", exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not download the Nasdaq symbol lists; previous lists were kept", exception);
        }
        try {
            Files.createDirectories(storageDirectory);
            for (Map.Entry<NasdaqSymbolFile, String> download : downloads.entrySet()) {
                write(download.getKey().storedFileName(), download.getValue());
            }
        } catch (IOException exception) {
            log.error("Nasdaq symbol sync could not write to {}: {}", storageDirectory, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store the Nasdaq symbol lists", exception);
        }
        snapshot = null;
        List<NasdaqSymbol> symbols = merge(parsed);
        int etfs = (int) symbols.stream().filter(symbol -> symbol.type() == NasdaqSymbolType.ETF).count();
        int equities = symbols.size() - etfs;
        log.info("Nasdaq symbol sync saved {} equities and {} ETFs to {}", equities, etfs, storageDirectory);
        return new SyncResult(equities, etfs, Instant.now());
    }

    public boolean hasStoredSymbolLists() {
        for (NasdaqSymbolFile file : NasdaqSymbolFile.values()) {
            if (!Files.isRegularFile(storageDirectory.resolve(file.storedFileName()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Symbols whose ticker or name contains {@code query} (case-insensitive), best matches first:
     * exact symbol, symbol prefix, name prefix or word prefix, then any other match.
     *
     * @param query optional search text; blank returns every symbol ordered by ticker
     * @param type  optional EQUITY or ETF filter; null returns both
     * @param limit optional maximum number of results; null returns all matches
     */
    public List<NasdaqSymbol> search(String query, NasdaqSymbolType type, Integer limit) {
        String needle = query == null ? "" : query.trim().toUpperCase(Locale.ROOT);
        Stream<NasdaqSymbol> matches = symbols().stream()
                .filter(symbol -> type == null || symbol.type() == type)
                .filter(symbol -> needle.isEmpty() || rank(symbol, needle) < NO_MATCH)
                .sorted(Comparator.<NasdaqSymbol>comparingInt(symbol -> needle.isEmpty() ? 0 : rank(symbol, needle))
                        .thenComparing(NasdaqSymbol::symbol));
        return (limit == null ? matches : matches.limit(limit)).toList();
    }

    private static int rank(NasdaqSymbol symbol, String needle) {
        String ticker = symbol.symbol();
        String name = symbol.name() == null ? "" : symbol.name().toUpperCase(Locale.ROOT);
        if (ticker.equals(needle)) {
            return 0;
        }
        if (ticker.startsWith(needle)) {
            return 1;
        }
        if (name.startsWith(needle)) {
            return 2;
        }
        if (name.contains(" " + needle)) {
            return 3;
        }
        if (ticker.contains(needle) || name.contains(needle)) {
            return 4;
        }
        return NO_MATCH;
    }

    private List<NasdaqSymbol> symbols() {
        if (!hasStoredSymbolLists()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Nasdaq symbol lists are not downloaded yet; call POST /api/us/nasdaq/sync");
        }
        try {
            Map<NasdaqSymbolFile, FileTime> modifiedTimes = new EnumMap<>(NasdaqSymbolFile.class);
            for (NasdaqSymbolFile file : NasdaqSymbolFile.values()) {
                modifiedTimes.put(file, Files.getLastModifiedTime(storageDirectory.resolve(file.storedFileName())));
            }
            Snapshot current = snapshot;
            if (current != null && current.modifiedTimes().equals(modifiedTimes)) {
                return current.symbols();
            }
            Map<NasdaqSymbolFile, List<NasdaqSymbol>> parsed = new EnumMap<>(NasdaqSymbolFile.class);
            for (NasdaqSymbolFile file : NasdaqSymbolFile.values()) {
                Path path = storageDirectory.resolve(file.storedFileName());
                parsed.put(file, NasdaqSymbolFileParser.parse(Files.readString(path, StandardCharsets.UTF_8), file));
            }
            List<NasdaqSymbol> loaded = merge(parsed);
            snapshot = new Snapshot(modifiedTimes, loaded);
            log.info("Loaded {} Nasdaq symbols from {}", loaded.size(), storageDirectory);
            return loaded;
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Could not read the stored Nasdaq symbol lists in {}: {}", storageDirectory,
                    exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored Nasdaq symbol lists could not be read; call POST /api/us/nasdaq/sync", exception);
        }
    }

    /** One row per ticker; the NASDAQ file wins if a ticker ever appears in both files. */
    private static List<NasdaqSymbol> merge(Map<NasdaqSymbolFile, List<NasdaqSymbol>> parsed) {
        Map<String, NasdaqSymbol> symbolsByTicker = new LinkedHashMap<>();
        for (List<NasdaqSymbol> symbols : parsed.values()) {
            symbols.forEach(symbol -> symbolsByTicker.putIfAbsent(symbol.symbol(), symbol));
        }
        return List.copyOf(symbolsByTicker.values());
    }

    private void write(String fileName, String text) throws IOException {
        Path target = storageDirectory.resolve(fileName);
        Path temp = Files.createTempFile(storageDirectory, fileName, ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
