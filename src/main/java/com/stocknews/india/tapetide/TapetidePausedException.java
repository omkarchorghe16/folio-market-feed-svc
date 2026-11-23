package com.stocknews.india.tapetide;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Thrown without any network call while {@link TapetideClient} is paused after a quota, plan-expired or
 * rejected-credentials answer. Callers serve saved rows instead.
 */
public class TapetidePausedException extends ResponseStatusException {
    public TapetidePausedException(Instant pausedUntil, String pauseReason) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Tapetide calls are paused until " + pausedUntil + ": " + pauseReason);
    }
}
