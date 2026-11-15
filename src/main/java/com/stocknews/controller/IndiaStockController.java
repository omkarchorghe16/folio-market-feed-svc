package com.stocknews.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.stocknews.india.tapetide.TapetideStockService;
import com.stocknews.india.upstox.UpstoxMarketDataService;
import com.stocknews.model.TapetideStockDataset;
import com.stocknews.model.TapetideStockQuote;
import com.stocknews.model.UpstoxStockCandle;
import com.stocknews.model.UpstoxStockQuote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Indian (NSE/BSE) stock data, saved to PostgreSQL with created/updated dates. Tapetide supplies
 * fundamentals (free: 50 calls/day; fresh rows are served from the database); Upstox supplies live
 * quotes and candles.
 *
 * <p>Bad symbols, bad options and provider failures never produce an error response: they are logged
 * with details and skipped. Single-symbol endpoints return 204 when there is nothing to return.
 */
@RestController
@RequestMapping("/api/india")
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(name = "India Stocks", description = "NSE/BSE fundamentals (Tapetide) and market data (Upstox), saved to PostgreSQL")
public class IndiaStockController {
    private final TapetideStockService tapetide;
    private final UpstoxMarketDataService upstox;

    // ---- Tapetide: fundamentals ----

    @GetMapping("/tapetide/search")
    @Operation(summary = "Find an NSE/BSE symbol by company name, symbol, BSE code or ISIN (1 Tapetide call; not saved)")
    public JsonNode search(@RequestParam(required = false) String query,
                           @RequestParam(required = false) String limit) {
        return tapetide.search(query, integer(limit));
    }

    @GetMapping("/tapetide/quote/{symbol}")
    @Operation(summary = "Tapetide quote: price, P/E (TTM), P/B, 52-week high/low",
            description = "Served from PostgreSQL when refreshed within tapetide.quote-refresh-minutes; otherwise 1 Tapetide call.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Saved quote"),
            @ApiResponse(responseCode = "204", description = "Symbol invalid/not found and nothing saved (see logs)")})
    public ResponseEntity<TapetideStockQuote> tapetideQuote(@PathVariable String symbol) {
        return single(tapetide.quote(symbol), "Tapetide quote", symbol);
    }

    @GetMapping("/tapetide/quotes")
    @Operation(summary = "Tapetide quotes for many symbols (1 call per 20 symbols to refresh); bad symbols are skipped")
    public List<TapetideStockQuote> tapetideQuotes(
            @Parameter(description = "Symbols, repeated or comma-separated", example = "RELIANCE,TCS,HDFCBANK")
            @RequestParam(required = false) @Size(max = 200) List<String> symbols) {
        List<TapetideStockQuote> quotes = tapetide.quotes(symbols);
        log.info("GET /api/india/tapetide/quotes returning {} of {} requested symbols",
                quotes.size(), symbols == null ? 0 : symbols.size());
        return quotes;
    }

    @GetMapping("/tapetide/profile/{symbol}")
    @Operation(summary = "Company overview with fundamentals, growth, pros/cons",
            description = "include: technicals, ratings, peers. Served from PostgreSQL when refreshed within tapetide.data-refresh-minutes.")
    public ResponseEntity<TapetideStockDataset> profile(@PathVariable String symbol,
                                                        @RequestParam(required = false) List<String> include) {
        return single(tapetide.profile(symbol, include), "Tapetide profile", symbol);
    }

    @GetMapping("/tapetide/financials/{symbol}")
    @Operation(summary = "Quarterly P&L and annual P&L, balance sheet, cash flow, ratios",
            description = "section: quarterly, profit_loss, balance_sheet, cash_flow or ratios (omit for all); periods: 1-40")
    public ResponseEntity<TapetideStockDataset> financials(@PathVariable String symbol,
                                                           @RequestParam(required = false) String section,
                                                           @RequestParam(required = false) String periods) {
        return single(tapetide.financials(symbol, section, integer(periods)), "Tapetide financials", symbol);
    }

    @GetMapping("/tapetide/shareholding/{symbol}")
    @Operation(summary = "Promoter, FII, DII and public holdings by period (type: quarterly or annual)")
    public ResponseEntity<TapetideStockDataset> shareholding(@PathVariable String symbol,
                                                             @RequestParam(required = false) String type) {
        return single(tapetide.shareholding(symbol, type), "Tapetide shareholding", symbol);
    }

    @GetMapping("/tapetide/forecasts/{symbol}")
    @Operation(summary = "Analyst estimates vs actuals: EPS, revenue, EBITDA, net income, ROA, ROE and price target")
    public ResponseEntity<TapetideStockDataset> forecasts(@PathVariable String symbol) {
        return single(tapetide.forecasts(symbol), "Tapetide forecasts", symbol);
    }

    @GetMapping("/tapetide/history/{symbol}")
    @Operation(summary = "Daily or weekly OHLCV with delivery % (days up to 2000; interval daily or weekly)")
    public ResponseEntity<TapetideStockDataset> tapetideHistory(@PathVariable String symbol,
                                                                @RequestParam(required = false) String days,
                                                                @RequestParam(required = false) String interval) {
        return single(tapetide.priceHistory(symbol, integer(days), interval), "Tapetide history", symbol);
    }

    // ---- Upstox: market data ----

    @GetMapping("/upstox/quotes")
    @Operation(summary = "Upstox live quotes, saved to PostgreSQL; unknown symbols are logged and skipped")
    public List<UpstoxStockQuote> upstoxQuotes(
            @Parameter(description = "Trading symbols, repeated or comma-separated", example = "RELIANCE,TCS")
            @RequestParam(required = false) @Size(max = 500) List<String> symbols,
            @RequestParam(defaultValue = "NSE") String exchange) {
        List<UpstoxStockQuote> quotes = upstox.quotes(symbols, exchange);
        log.info("GET /api/india/upstox/quotes returning {} of {} requested symbols",
                quotes.size(), symbols == null ? 0 : symbols.size());
        return quotes;
    }

    @GetMapping("/upstox/candles/{symbol}")
    @Operation(summary = "Upstox historical candles, saved to PostgreSQL",
            description = "unit: minutes (interval 1-300, from Jan 2022), hours (1-5), days, weeks, months (interval 1, from Jan 2000). "
                    + "from/to: yyyy-MM-dd (default: last month). Upstox caps one call at 1 month for 1-15 minute candles, "
                    + "1 quarter for larger minute/hour candles, and 1 decade for daily candles.")
    public List<UpstoxStockCandle> candles(@PathVariable String symbol,
                                           @RequestParam(defaultValue = "NSE") String exchange,
                                           @RequestParam(defaultValue = "days") String unit,
                                           @RequestParam(defaultValue = "1") String interval,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        Integer size = integer(interval);
        if (size == null || size < 1) {
            log.error("Upstox candles skipped for {}: interval '{}' must be a positive whole number", symbol, interval);
            return List.of();
        }
        return upstox.candles(symbol, exchange, unit, size, from, to);
    }

    /** Lenient number parsing so a bad value is logged instead of failing the request. */
    private static Integer integer(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            log.error("Ignoring invalid number '{}' in request; using the default instead", value);
            return null;
        }
    }

    private static <T> ResponseEntity<T> single(Optional<T> result, String what, String symbol) {
        if (result.isEmpty()) {
            log.warn("{} for '{}': nothing to return (see the error logged above); responding 204", what, symbol);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result.get());
    }
}
