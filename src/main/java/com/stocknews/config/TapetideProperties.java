package com.stocknews.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tapetide (NSE/BSE fundamentals). The free plan covers 100 tool calls a day for the first 7 days after signup;
 * after that a paid plan is needed.
 * Get a personal token (starts with {@code tpt_rt_}) at https://tapetide.com/settings/tokens.
 */
@Component
@ConfigurationProperties(prefix = "tapetide")
@Data
public class TapetideProperties {
    /** Host serving both {@code /token} (refresh-token exchange) and {@code /mcp} (JSON-RPC tool calls). */
    private String baseUrl = "https://mcp.tapetide.com";
    /** Personal refresh token from tapetide.com/settings/tokens. */
    private String token;
    /** A saved quote younger than this is returned from PostgreSQL without calling Tapetide. */
    private int quoteRefreshMinutes = 60;
    /** A saved profile/financials/shareholding/forecasts/history row younger than this is served from PostgreSQL. */
    private int dataRefreshMinutes = 1440;
    private int timeoutSeconds = 30;
    /**
     * After a quota, plan-expired or rejected-credentials answer, no Tapetide calls are made for this long (saved rows
     * are served instead). A 429 with Retry-After uses that delay instead.
     */
    private int pauseMinutes = 180;
}
