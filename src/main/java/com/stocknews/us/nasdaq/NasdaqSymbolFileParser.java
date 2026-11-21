package com.stocknews.us.nasdaq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses Nasdaq Trader's pipe-delimited nasdaqlisted.txt and otherlisted.txt. Columns are found by
 * header name (ignoring case, spaces and punctuation). Test issues are skipped. A file without the
 * closing "File Creation Time" line is treated as a truncated download and rejected.
 */
final class NasdaqSymbolFileParser {
    private static final String FOOTER_PREFIX = "File Creation Time";
    private static final List<String> SYMBOL_HEADERS = List.of("SYMBOL", "ACTSYMBOL");
    private static final List<String> NAME_HEADERS = List.of("SECURITYNAME");
    private static final List<String> ETF_HEADERS = List.of("ETF");
    private static final List<String> TEST_ISSUE_HEADERS = List.of("TESTISSUE");
    private static final List<String> EXCHANGE_HEADERS = List.of("EXCHANGE");
    private static final String NASDAQ_EXCHANGE = "NASDAQ";
    /** Exchange codes documented by Nasdaq Trader for otherlisted.txt. */
    private static final Map<String, String> EXCHANGE_NAMES = Map.of(
            "N", "NYSE",
            "A", "NYSE American",
            "P", "NYSE Arca",
            "Z", "Cboe BZX",
            "V", "IEX");

    private NasdaqSymbolFileParser() {
    }

    /**
     * @throws IllegalArgumentException when the file is empty, truncated, missing a required column or has no rows
     */
    static List<NasdaqSymbol> parse(String text, NasdaqSymbolFile file) {
        List<String> lines = text.strip().lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Nasdaq " + file + " list is empty");
        }
        if (!lines.get(lines.size() - 1).startsWith(FOOTER_PREFIX)) {
            throw new IllegalArgumentException("Nasdaq " + file + " list has no '" + FOOTER_PREFIX
                    + "' line; the download looks truncated");
        }
        Map<String, Integer> columns = headerIndex(splitLine(lines.get(0)));
        int symbolColumn = column(columns, SYMBOL_HEADERS);
        int nameColumn = column(columns, NAME_HEADERS);
        int etfColumn = column(columns, ETF_HEADERS);
        if (symbolColumn < 0 || nameColumn < 0 || etfColumn < 0) {
            throw new IllegalArgumentException("Nasdaq " + file + " list has no symbol/name/ETF header");
        }
        int testIssueColumn = column(columns, TEST_ISSUE_HEADERS);
        int exchangeColumn = column(columns, EXCHANGE_HEADERS);

        List<NasdaqSymbol> symbols = new ArrayList<>(lines.size());
        for (String line : lines.subList(1, lines.size() - 1)) {
            List<String> cells = splitLine(line);
            String symbol = cell(cells, symbolColumn);
            if (symbol == null || "Y".equals(cell(cells, testIssueColumn))) {
                continue;
            }
            NasdaqSymbolType type = "Y".equals(cell(cells, etfColumn)) ? NasdaqSymbolType.ETF : NasdaqSymbolType.EQUITY;
            String exchange = file == NasdaqSymbolFile.NASDAQ_LISTED
                    ? NASDAQ_EXCHANGE
                    : exchangeName(cell(cells, exchangeColumn));
            symbols.add(new NasdaqSymbol(symbol.toUpperCase(Locale.ROOT), cell(cells, nameColumn), type, exchange));
        }
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("Nasdaq " + file + " list has a header but no rows");
        }
        return symbols;
    }

    private static List<String> splitLine(String line) {
        return Arrays.asList(line.split("\\|", -1));
    }

    private static String exchangeName(String code) {
        if (code == null) {
            return null;
        }
        return EXCHANGE_NAMES.getOrDefault(code, code);
    }

    private static Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int position = 0; position < headers.size(); position++) {
            String normalized = headers.get(position).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            index.putIfAbsent(normalized, position);
        }
        return index;
    }

    private static int column(Map<String, Integer> columns, List<String> names) {
        return names.stream().filter(columns::containsKey).map(columns::get).findFirst().orElse(-1);
    }

    private static String cell(List<String> cells, int column) {
        if (column < 0 || column >= cells.size()) {
            return null;
        }
        String value = cells.get(column).trim();
        return value.isEmpty() ? null : value;
    }
}
