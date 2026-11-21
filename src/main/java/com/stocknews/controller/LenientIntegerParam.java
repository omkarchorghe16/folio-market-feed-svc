package com.stocknews.controller;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses optional whole-number request parameters leniently, so a bad value is logged and the default
 * is used instead of the request failing with 400. Used by the India market-data controllers.
 */
@Slf4j
final class LenientIntegerParam {

    private LenientIntegerParam() {
    }

    /** Returns {@code null} for a missing, blank or non-numeric value. */
    static Integer parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            log.error("Ignoring invalid number '{}' in request; using the default instead", value);
            return null;
        }
    }
}
