package com.stocknews.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Upstox market data (live quotes, historical candles). Use the free, read-only Analytics Token
 * (1-year validity) from https://account.upstox.com/developer/apps → Analytics tab.
 */
@Component
@ConfigurationProperties(prefix = "upstox")
@Data
public class UpstoxProperties {
    private String baseUrl = "https://api.upstox.com";
    /** Analytics Token (read-only, ~1 year) or a daily access token. */
    private String accessToken;
    /** {exchange} is NSE or BSE. Upstox refreshes these files daily around 6 AM IST. */
    private String instrumentsUrl = "https://assets.upstox.com/market-quote/instruments/exchange/{exchange}.json.gz";
    /** Time zone used to decide when the cached instrument list is stale. */
    private String timeZone = "Asia/Kolkata";
}
