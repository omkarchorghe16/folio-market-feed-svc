package com.stocknews.dto;

import com.stocknews.india.tapetide.TapetideAvailability;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Whether folio-market-feed-svc is currently calling Tapetide")
public record TapetideStatusResponse(
        @Schema(description = "False while calls are paused after a quota, plan-expired or rejected-token answer")
        boolean available,
        @Schema(description = "When calls resume (UTC); null when available", example = "2026-11-23T06:30:00Z")
        Instant pausedUntil,
        @Schema(description = "Why calls are paused, for example Tapetide's plan message; null when available")
        String reason
) {
    public static TapetideStatusResponse from(TapetideAvailability availability) {
        return new TapetideStatusResponse(availability.available(), availability.pausedUntil(), availability.reason());
    }
}
