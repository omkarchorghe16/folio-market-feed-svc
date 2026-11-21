package com.stocknews.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nasdaq Trader's free, official US symbol directory (no API key). A daily job downloads
 * nasdaqlisted.txt (NASDAQ) and otherlisted.txt (NYSE, NYSE American, NYSE Arca, Cboe, IEX) into
 * {@link #storageDirectory}; the US symbol search endpoint reads those files.
 */
@Component
@ConfigurationProperties(prefix = "nasdaq")
@Data
public class NasdaqProperties {
    /** Every security listed on NASDAQ: Symbol|Security Name|Market Category|Test Issue|...|ETF|NextShares. */
    private String nasdaqListedUrl = "https://www.nasdaqtrader.com/dynamic/SymDir/nasdaqlisted.txt";
    /** Every security listed on the other US exchanges: ACT Symbol|Security Name|Exchange|...|ETF|...|Test Issue. */
    private String otherListedUrl = "https://www.nasdaqtrader.com/dynamic/SymDir/otherlisted.txt";
    /** Directory that holds the downloaded nasdaq-listed.txt and other-listed.txt files. */
    private String storageDirectory = "./data/us";
    private int timeoutSeconds = 30;
    /** Download the files when the application starts and they are not on disk yet. */
    private boolean syncOnStartup = true;
}
