package com.stocknews.controller;

import com.stocknews.india.nse.NseSymbol;
import com.stocknews.india.nse.NseSymbolService;
import com.stocknews.india.nse.NseSymbolType;
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
 * NSE equity and ETF ticker symbols for type-ahead dropdowns, from NSE's official CSV lists
 * (downloaded weekly by {@code NseSymbolSyncScheduler}; no API key needed).
 */
@RestController
@RequestMapping("/api/india/nse")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "India NSE Symbols", description = "All NSE stock and ETF ticker symbols and names, synced weekly from NSE's CSV files")
public class NseSymbolController {
    private final NseSymbolService nseSymbolService;

    @GetMapping("/symbols")
    @Operation(summary = "List or search NSE stock and ETF symbols",
            description = "Matches the ticker or name (case-insensitive). Best matches come first: exact ticker, "
                    + "ticker prefix, name prefix, word prefix, then any other match. Omit query for the full list.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Matching symbols"),
            @ApiResponse(responseCode = "400", description = "Invalid type, limit or query"),
            @ApiResponse(responseCode = "503", description = "Lists not downloaded yet; call POST /api/india/nse/sync")})
    public List<NseSymbol> symbols(
            @Parameter(description = "Ticker or name text", example = "reli")
            @RequestParam(required = false) @Size(max = 100) String query,
            @Parameter(description = "EQUITY or ETF; omit for both")
            @RequestParam(required = false) NseSymbolType type,
            @Parameter(description = "Maximum results (1-5000); omit for all matches", example = "20")
            @RequestParam(required = false) @Min(1) @Max(5000) Integer limit) {
        List<NseSymbol> symbols = nseSymbolService.search(query, type, limit);
        log.debug("GET /api/india/nse/symbols returning {} symbols (type={}, limit={})", symbols.size(), type, limit);
        return symbols;
    }

    @PostMapping("/sync")
    @Operation(summary = "Download the NSE equity and ETF lists now (the scheduler does this weekly)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lists downloaded and stored"),
            @ApiResponse(responseCode = "502", description = "NSE download failed; the previous lists were kept")})
    public NseSymbolService.SyncResult sync() {
        return nseSymbolService.sync();
    }
}
