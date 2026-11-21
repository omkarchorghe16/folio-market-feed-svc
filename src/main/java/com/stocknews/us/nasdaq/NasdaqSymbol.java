package com.stocknews.us.nasdaq;

/**
 * One US-listed security from Nasdaq Trader's symbol directory.
 *
 * @param symbol   trading symbol as Nasdaq Trader publishes it, for example {@code AAPL} or {@code BRK.B}
 * @param name     security name, for example {@code Apple Inc. - Common Stock}
 * @param type     {@link NasdaqSymbolType#EQUITY} or {@link NasdaqSymbolType#ETF}
 * @param exchange listing exchange: {@code NASDAQ}, {@code NYSE}, {@code NYSE American}, {@code NYSE Arca},
 *                 {@code Cboe BZX} or {@code IEX}; any other exchange is returned as its one-letter code
 */
public record NasdaqSymbol(String symbol, String name, NasdaqSymbolType type, String exchange) {
}
