package com.stocknews.india;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Normalizes NSE/BSE symbols and logs (instead of rejecting) ones that cannot be valid. */
@Slf4j
public final class IndiaSymbols {
    /** RELIANCE, M&M, BAJAJ-AUTO, NAM-INDIA, 500325 (BSE code). */
    private static final Pattern VALID = Pattern.compile("[A-Z0-9][A-Z0-9&._-]{0,29}");

    private IndiaSymbols() {
    }

    /**
     * Trims, upper-cases and strips a {@code .NS}/{@code .BO} suffix. Returns empty (after logging an
     * error naming {@code context}) when the input is blank or contains characters no symbol can have.
     */
    public static Optional<String> normalize(String raw, String context) {
        if (raw == null || raw.isBlank()) {
            log.error("{} skipped: symbol is empty", context);
            return Optional.empty();
        }
        String symbol = raw.trim().toUpperCase(Locale.ROOT);
        if (symbol.endsWith(".NS") || symbol.endsWith(".BO")) {
            symbol = symbol.substring(0, symbol.length() - 3);
        }
        if (!VALID.matcher(symbol).matches()) {
            log.error("{} skipped: '{}' is not a valid NSE/BSE symbol (letters, digits, & . _ - only, max 30)",
                    context, raw);
            return Optional.empty();
        }
        return Optional.of(symbol);
    }
}
