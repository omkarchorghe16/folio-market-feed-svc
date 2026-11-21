package com.stocknews.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.stocknews.india.tapetide.TapetideStockService;
import com.stocknews.model.TapetideStockDataset;
import com.stocknews.model.TapetideStockQuote;
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
 * Indian (NSE/BSE) fundamentals from Tapetide, saved to PostgreSQL with created/updated dates
 * (free plan: 50 calls/day; fresh rows are served from the database).
 *
 * <p>Bad symbols, bad options and provider failures never produce an error response: they are logged
 * with details and skipped. Single-symbol endpoints return 204 when there is nothing to return.
 */
@RestController
@RequestMapping("/api/india/tapetide")
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(name = "India Stocks (Tapetide)", description = "NSE/BSE fundamentals from Tapetide, saved to PostgreSQL")
public class TapetideStockController {

    private final TapetideStockService tapetideStockService;

    @GetMapping("/search")
    @Operation(summary = "Find an NSE/BSE symbol by company name, symbol, BSE code or ISIN (1 Tapetide call; not saved)")
    public JsonNode searchSymbols(@RequestParam(required = false) String query,
                                  @RequestParam(required = false) String limit) {
        return tapetideStockService.searchSymbols(query, LenientIntegerParam.parse(limit));
    }

    @GetMapping("/quote/{symbol}")
    @Operation(summary = "Tapetide quote: price, P/E (TTM), P/B, 52-week high/low",
            description = "Served from PostgreSQL when refreshed within tapetide.quote-refresh-minutes; "
                    + "otherwise 1 Tapetide call.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Saved quote"),
            @ApiResponse(responseCode = "204", description = "Symbol invalid/not found and nothing saved (see logs)")})
    public ResponseEntity<TapetideStockQuote> getQuote(@PathVariable String symbol) {
        return okOrNoContent(tapetideStockService.getQuote(symbol), "Tapetide quote", symbol);
    }

    @GetMapping("/quotes")
    @Operation(summary = "Tapetide quotes for many symbols (1 call per 20 symbols to refresh); bad symbols are skipped")
    public List<TapetideStockQuote> getQuotes(
            @Parameter(description = "Symbols, repeated or comma-separated", example = "RELIANCE,TCS,HDFCBANK")
            @RequestParam(required = false) @Size(max = 200) List<String> symbols) {
        List<TapetideStockQuote> quotes = tapetideStockService.getQuotes(symbols);
        log.info("GET /api/india/tapetide/quotes returning {} of {} requested symbols",
                quotes.size(), symbols == null ? 0 : symbols.size());
        return quotes;
    }

    @GetMapping("/profile/{symbol}")
    @Operation(summary = "Company overview with fundamentals, growth, pros/cons",
            description = "include: technicals, ratings, peers. Served from PostgreSQL when refreshed within "
                    + "tapetide.data-refresh-minutes.")
    public ResponseEntity<TapetideStockDataset> getProfile(
            @PathVariable String symbol,
            @RequestParam(required = false) List<String> include) {
        return okOrNoContent(tapetideStockService.getProfile(symbol, include), "Tapetide profile", symbol);
    }

    @GetMapping("/financials/{symbol}")
    @Operation(summary = "Quarterly P&L and annual P&L, balance sheet, cash flow, ratios",
            description = "section: quarterly, profit_loss, balance_sheet, cash_flow or ratios (omit for all); "
                    + "periods: 1-40")
    public ResponseEntity<TapetideStockDataset> getFinancials(
            @PathVariable String symbol,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String periods) {
        Optional<TapetideStockDataset> financials =
                tapetideStockService.getFinancials(symbol, section, LenientIntegerParam.parse(periods));
        return okOrNoContent(financials, "Tapetide financials", symbol);
    }

    @GetMapping("/shareholding/{symbol}")
    @Operation(summary = "Promoter, FII, DII and public holdings by period (type: quarterly or annual)")
    public ResponseEntity<TapetideStockDataset> getShareholding(
            @PathVariable String symbol,
            @RequestParam(required = false) String type) {
        return okOrNoContent(tapetideStockService.getShareholding(symbol, type), "Tapetide shareholding", symbol);
    }

    @GetMapping("/forecasts/{symbol}")
    @Operation(summary = "Analyst estimates vs actuals: EPS, revenue, EBITDA, net income, ROA, ROE and price target")
    public ResponseEntity<TapetideStockDataset> getForecasts(@PathVariable String symbol) {
        return okOrNoContent(tapetideStockService.getForecasts(symbol), "Tapetide forecasts", symbol);
    }

    @GetMapping("/history/{symbol}")
    @Operation(summary = "Daily or weekly OHLCV with delivery % (days up to 2000; interval daily or weekly)")
    public ResponseEntity<TapetideStockDataset> getPriceHistory(
            @PathVariable String symbol,
            @RequestParam(required = false) String days,
            @RequestParam(required = false) String interval) {
        Optional<TapetideStockDataset> history =
                tapetideStockService.getPriceHistory(symbol, LenientIntegerParam.parse(days), interval);
        return okOrNoContent(history, "Tapetide history", symbol);
    }

    private static <T> ResponseEntity<T> okOrNoContent(Optional<T> result, String dataName, String symbol) {
        if (result.isEmpty()) {
            log.warn("{} for '{}': nothing to return (see the error logged above); responding 204", dataName, symbol);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result.get());
    }
}
