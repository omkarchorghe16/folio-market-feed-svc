package com.stocknews.india.nse;

/**
 * One NSE-listed security from the official symbol lists.
 *
 * @param symbol NSE trading symbol, for example {@code RELIANCE} or {@code NIFTYBEES}
 * @param name   company name (equities) or security name (ETFs)
 * @param type   {@link NseSymbolType#EQUITY} or {@link NseSymbolType#ETF}
 * @param series NSE series such as {@code EQ} or {@code BE} (equities only; null for ETFs)
 * @param isin   ISIN, for example {@code INE002A01018}
 */
public record NseSymbol(String symbol, String name, NseSymbolType type, String series, String isin) {
}
