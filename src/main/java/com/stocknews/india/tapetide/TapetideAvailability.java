package com.stocknews.india.tapetide;

import java.time.Instant;

/**
 * Whether {@link TapetideClient} is making calls. While paused, {@code pausedUntil} and {@code reason} say until when
 * and why (for example Tapetide's "free access is now over" message); both are null when available.
 */
public record TapetideAvailability(boolean available, Instant pausedUntil, String reason) {
}
