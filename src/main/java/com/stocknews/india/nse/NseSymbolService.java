package com.stocknews.india.nse;

import com.stocknews.config.NseProperties;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Keeps NSE's equity and ETF symbol lists as CSV files on disk and searches them for type-ahead
 * dropdowns. {@link #refreshSymbolLists()} replaces both files only after both downloads parse successfully, so a
 * failed or partial download never removes the previous lists. Searches read the CSV files and keep
 * the parsed rows in memory until a file changes on disk.
 */
@Service
@Slf4j
public class NseSymbolService {
    static final String EQUITY_FILE = "nse-equities.csv";
    static final String ETF_FILE = "nse-etfs.csv";

    public record SyncResult(int equities, int etfs, Instant syncedAt) {
    }

    private record Snapshot(FileTime equityModified, FileTime etfModified, List<NseSymbol> symbols) {
    }

    private final NseSymbolListClient nseSymbolListClient;
    private final Path storageDirectory;
    private volatile Snapshot snapshot;

    public NseSymbolService(NseSymbolListClient nseSymbolListClient, NseProperties nseProperties) {
        this.nseSymbolListClient = nseSymbolListClient;
        this.storageDirectory = Path.of(nseProperties.getStorageDirectory());
    }

    /** Downloads both NSE lists and atomically replaces the stored CSV files. */
    public synchronized SyncResult refreshSymbolLists() {
        String equityCsv;
        String etfCsv;
        int equities;
        int etfs;
        try {
            equityCsv = nseSymbolListClient.fetchSymbolList(NseSymbolType.EQUITY);
            etfCsv = nseSymbolListClient.fetchSymbolList(NseSymbolType.ETF);
            equities = NseSymbolCsvParser.parse(equityCsv, NseSymbolType.EQUITY).size();
            etfs = NseSymbolCsvParser.parse(etfCsv, NseSymbolType.ETF).size();
        } catch (IOException | IllegalArgumentException exception) {
            log.error("NSE symbol sync failed; keeping the previously stored lists: {}", exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not download the NSE symbol lists; previous lists were kept", exception);
        }
        try {
            Files.createDirectories(storageDirectory);
            write(EQUITY_FILE, equityCsv);
            write(ETF_FILE, etfCsv);
        } catch (IOException exception) {
            log.error("NSE symbol sync could not write to {}: {}", storageDirectory, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store the NSE symbol lists", exception);
        }
        snapshot = null;
        log.info("NSE symbol sync saved {} equities and {} ETFs to {}", equities, etfs, storageDirectory);
        return new SyncResult(equities, etfs, Instant.now());
    }

    public boolean hasStoredSymbolLists() {
        return Files.isRegularFile(storageDirectory.resolve(EQUITY_FILE))
                && Files.isRegularFile(storageDirectory.resolve(ETF_FILE));
    }

    /**
     * Symbols whose ticker or name contains {@code query} (case-insensitive), best matches first:
     * exact symbol, symbol prefix, name prefix or word prefix, then any other match.
     *
     * @param query optional search text; blank returns every symbol ordered by ticker
     * @param type  optional EQUITY or ETF filter; null returns both
     * @param limit optional maximum number of results; null returns all matches
     */
    public List<NseSymbol> search(String query, NseSymbolType type, Integer limit) {
        String needle = query == null ? "" : query.trim().toUpperCase(Locale.ROOT);
        Stream<NseSymbol> matches = symbols().stream()
                .filter(symbol -> type == null || symbol.type() == type)
                .filter(symbol -> needle.isEmpty() || rank(symbol, needle) < Integer.MAX_VALUE)
                .sorted(Comparator.<NseSymbol>comparingInt(symbol -> needle.isEmpty() ? 0 : rank(symbol, needle))
                        .thenComparing(NseSymbol::symbol));
        return (limit == null ? matches : matches.limit(limit)).toList();
    }

    private static int rank(NseSymbol symbol, String needle) {
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
        return Integer.MAX_VALUE;
    }

    private List<NseSymbol> symbols() {
        Path equityPath = storageDirectory.resolve(EQUITY_FILE);
        Path etfPath = storageDirectory.resolve(ETF_FILE);
        if (!hasStoredSymbolLists()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NSE symbol lists are not downloaded yet; call POST /api/india/nse/sync");
        }
        try {
            FileTime equityModified = Files.getLastModifiedTime(equityPath);
            FileTime etfModified = Files.getLastModifiedTime(etfPath);
            Snapshot current = snapshot;
            if (current != null && current.equityModified().equals(equityModified)
                    && current.etfModified().equals(etfModified)) {
                return current.symbols();
            }
            List<NseSymbol> loaded = merge(
                    NseSymbolCsvParser.parse(Files.readString(equityPath, StandardCharsets.UTF_8), NseSymbolType.EQUITY),
                    NseSymbolCsvParser.parse(Files.readString(etfPath, StandardCharsets.UTF_8), NseSymbolType.ETF));
            snapshot = new Snapshot(equityModified, etfModified, loaded);
            log.info("Loaded {} NSE symbols from {}", loaded.size(), storageDirectory);
            return loaded;
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Could not read the stored NSE symbol lists in {}: {}", storageDirectory, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored NSE symbol lists could not be read; call POST /api/india/nse/sync", exception);
        }
    }

    /** One row per ticker; an ETF row wins over an equity row with the same symbol. */
    private static List<NseSymbol> merge(List<NseSymbol> equities, List<NseSymbol> etfs) {
        Map<String, NseSymbol> bySymbol = new LinkedHashMap<>();
        equities.forEach(symbol -> bySymbol.putIfAbsent(symbol.symbol(), symbol));
        etfs.forEach(symbol -> bySymbol.put(symbol.symbol(), symbol));
        return List.copyOf(new ArrayList<>(bySymbol.values()));
    }

    private void write(String fileName, String csv) throws IOException {
        Path target = storageDirectory.resolve(fileName);
        Path temp = Files.createTempFile(storageDirectory, fileName, ".tmp");
        try {
            Files.writeString(temp, csv, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
