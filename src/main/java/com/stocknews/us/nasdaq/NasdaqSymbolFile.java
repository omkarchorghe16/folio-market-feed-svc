package com.stocknews.us.nasdaq;

/** The two Nasdaq Trader symbol-directory files that together cover every US-listed security. */
public enum NasdaqSymbolFile {
    /** nasdaqlisted.txt: securities listed on NASDAQ. */
    NASDAQ_LISTED("nasdaq-listed.txt"),
    /** otherlisted.txt: securities listed on NYSE, NYSE American, NYSE Arca, Cboe and IEX. */
    OTHER_LISTED("other-listed.txt");

    private final String storedFileName;

    NasdaqSymbolFile(String storedFileName) {
        this.storedFileName = storedFileName;
    }

    /** Name of the local copy in {@code nasdaq.storage-directory}. */
    public String storedFileName() {
        return storedFileName;
    }
}
