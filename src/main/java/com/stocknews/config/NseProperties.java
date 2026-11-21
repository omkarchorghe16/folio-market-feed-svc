package com.stocknews.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * NSE's free, official symbol lists (no API key). A weekly job downloads the equity and ETF CSV files
 * into {@link #storageDirectory}; the symbol search endpoint reads those files.
 */
@Component
@ConfigurationProperties(prefix = "nse")
@Data
public class NseProperties {
    /** All equities listed on NSE: SYMBOL, NAME OF COMPANY, SERIES, ..., ISIN NUMBER. */
    private String equityListUrl = "https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv";
    /** All ETFs listed on NSE: Symbol, Underlying, SecurityName, ..., ISINNumber. */
    private String etfListUrl = "https://nsearchives.nseindia.com/content/equities/eq_etfseclist.csv";
    /** Directory that holds the downloaded nse-equities.csv and nse-etfs.csv files. */
    private String storageDirectory = "./data/nse";
    /** NSE rejects requests that do not look like they come from a browser. */
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private int timeoutSeconds = 30;
    /** Download the files when the application starts and they are not on disk yet. */
    private boolean syncOnStartup = true;
}
