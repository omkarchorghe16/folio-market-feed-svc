package com.stocknews.controller;

import com.stocknews.us.nasdaq.NasdaqSymbol;
import com.stocknews.us.nasdaq.NasdaqSymbolService;
import com.stocknews.us.nasdaq.NasdaqSymbolType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * US stock and ETF ticker symbols for type-ahead dropdowns, from Nasdaq Trader's official symbol
 * directory, which covers NASDAQ, NYSE, NYSE American, NYSE Arca, Cboe and IEX (downloaded daily by
 * {@code NasdaqSymbolSyncScheduler}; no API key needed).
 */
@RestController
@RequestMapping("/api/us/nasdaq")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "US Symbols (Nasdaq Trader)",
        description = "All US-listed stock and ETF ticker symbols and names, synced daily from Nasdaq Trader")
public class NasdaqSymbolController {

    private final NasdaqSymbolService nasdaqSymbolService;

    @GetMapping("/symbols")
    @Operation(summary = "List or search US stock and ETF symbols",
            description = "Matches the ticker or name (case-insensitive). Best matches come first: exact ticker, "
                    + "ticker prefix, name prefix, word prefix, then any other match. Omit query for the full list.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Matching symbols"),
            @ApiResponse(responseCode = "400", description = "Invalid type, limit or query"),
            @ApiResponse(responseCode = "503",
                    description = "Lists not downloaded yet; call POST /api/us/nasdaq/sync")})
    public List<NasdaqSymbol> searchSymbols(
            @Parameter(description = "Ticker or name text", example = "appl")
            @RequestParam(required = false) @Size(max = 100) String query,
            @Parameter(description = "EQUITY or ETF; omit for both")
            @RequestParam(required = false) NasdaqSymbolType type,
            @Parameter(description = "Maximum results (1-20000); omit for all matches", example = "20")
            @RequestParam(required = false) @Min(1) @Max(20000) Integer limit) {
        List<NasdaqSymbol> symbols = nasdaqSymbolService.search(query, type, limit);
        log.debug("GET /api/us/nasdaq/symbols returning {} symbols (type={}, limit={})", symbols.size(), type, limit);
        return symbols;
    }

    @PostMapping("/sync")
    @Operation(summary = "Download the Nasdaq Trader US symbol lists now (the scheduler does this daily)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lists downloaded and stored"),
            @ApiResponse(responseCode = "502",
                    description = "Nasdaq Trader download failed; the previous lists were kept")})
    public NasdaqSymbolService.SyncResult refreshSymbolLists() {
        return nasdaqSymbolService.refreshSymbolLists();
    }
}
