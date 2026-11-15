package com.stocknews.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tapetide (NSE/BSE fundamentals). Free plan: 50 tool calls per day.
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
}
