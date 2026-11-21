package com.stocknews.india.nse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses NSE's EQUITY_L.csv and eq_etfseclist.csv. Columns are found by header name (ignoring case,
 * spaces and punctuation) so NSE re-ordering columns or padding headers with spaces does not break it.
 */
final class NseSymbolCsvParser {
    private static final List<String> SYMBOL_HEADERS = List.of("SYMBOL");
    private static final List<String> NAME_HEADERS = List.of("NAMEOFCOMPANY", "SECURITYNAME", "NAME");
    private static final List<String> SERIES_HEADERS = List.of("SERIES");
    private static final List<String> ISIN_HEADERS = List.of("ISINNUMBER", "ISIN", "ISINNO");

    private NseSymbolCsvParser() {
    }

    /**
     * @throws IllegalArgumentException when the file is empty, has no symbol/name column or no rows
     */
    static List<NseSymbol> parse(String csv, NseSymbolType type) {
        List<String> lines = csv.strip().lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("NSE " + type + " list is empty");
        }
        Map<String, Integer> columns = headerIndex(splitLine(stripBom(lines.get(0))));
        int symbolColumn = column(columns, SYMBOL_HEADERS);
        int nameColumn = column(columns, NAME_HEADERS);
        if (symbolColumn < 0 || nameColumn < 0) {
            throw new IllegalArgumentException("NSE " + type + " list has no symbol/name header");
        }
        int seriesColumn = column(columns, SERIES_HEADERS);
        int isinColumn = column(columns, ISIN_HEADERS);

        List<NseSymbol> symbols = new ArrayList<>(lines.size());
        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = splitLine(line);
            String symbol = cell(cells, symbolColumn);
            if (symbol == null) {
                continue;
            }
            symbols.add(new NseSymbol(symbol.toUpperCase(Locale.ROOT), cell(cells, nameColumn), type,
                    type == NseSymbolType.EQUITY ? cell(cells, seriesColumn) : null, cell(cells, isinColumn)));
        }
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("NSE " + type + " list has a header but no rows");
        }
        return symbols;
    }

    /** Splits one CSV line, honouring double-quoted cells that contain commas or escaped quotes. */
    static List<String> splitLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.putIfAbsent(headers.get(i).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""), i);
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

    private static String stripBom(String line) {
        return line.startsWith("﻿") ? line.substring(1) : line;
    }
}
