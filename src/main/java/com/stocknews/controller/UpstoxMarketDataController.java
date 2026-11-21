package com.stocknews.controller;

import com.stocknews.india.upstox.UpstoxMarketDataService;
import com.stocknews.model.UpstoxStockCandle;
import com.stocknews.model.UpstoxStockQuote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Indian (NSE/BSE) live quotes and historical candles from Upstox, saved to PostgreSQL with
 * created/updated dates.
 *
 * <p>Unknown symbols, bad options and provider failures never produce an error response: they are
 * logged with details and skipped.
 */
@RestController
@RequestMapping("/api/india/upstox")
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(name = "India Stocks (Upstox)", description = "NSE/BSE live quotes and candles from Upstox, saved to PostgreSQL")
public class UpstoxMarketDataController {

    private final UpstoxMarketDataService upstoxMarketDataService;

    @GetMapping("/quotes")
    @Operation(summary = "Upstox live quotes, saved to PostgreSQL; unknown symbols are logged and skipped")
    public List<UpstoxStockQuote> getQuotes(
            @Parameter(description = "Trading symbols, repeated or comma-separated", example = "RELIANCE,TCS")
            @RequestParam(required = false) @Size(max = 500) List<String> symbols,
            @RequestParam(defaultValue = "NSE") String exchange) {
        List<UpstoxStockQuote> quotes = upstoxMarketDataService.getQuotes(symbols, exchange);
        log.info("GET /api/india/upstox/quotes returning {} of {} requested symbols",
                quotes.size(), symbols == null ? 0 : symbols.size());
        return quotes;
    }

    @GetMapping("/candles/{symbol}")
    @Operation(summary = "Upstox historical candles, saved to PostgreSQL",
            description = "unit: minutes (interval 1-300, from Jan 2022), hours (1-5), days, weeks, months "
                    + "(interval 1, from Jan 2000). from/to: yyyy-MM-dd (default: last month). Upstox caps one call "
                    + "at 1 month for 1-15 minute candles, 1 quarter for larger minute/hour candles, and 1 decade "
                    + "for daily candles.")
    public List<UpstoxStockCandle> getCandles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "NSE") String exchange,
            @RequestParam(defaultValue = "days") String unit,
            @RequestParam(defaultValue = "1") String interval,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Integer intervalSize = LenientIntegerParam.parse(interval);
        if (intervalSize == null || intervalSize < 1) {
            log.error("Upstox candles skipped for {}: interval '{}' must be a positive whole number",
                    symbol, interval);
            return List.of();
        }
        return upstoxMarketDataService.getCandles(symbol, exchange, unit, intervalSize, from, to);
    }
}
