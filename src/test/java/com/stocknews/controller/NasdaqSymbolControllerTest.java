package com.stocknews.controller;

import com.stocknews.us.nasdaq.NasdaqSymbol;
import com.stocknews.us.nasdaq.NasdaqSymbolService;
import com.stocknews.us.nasdaq.NasdaqSymbolType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NasdaqSymbolController.class)
class NasdaqSymbolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NasdaqSymbolService nasdaqSymbolService;

    @Test
    void searchReturnsMatchingSymbols() throws Exception {
        when(nasdaqSymbolService.search("appl", NasdaqSymbolType.EQUITY, 20)).thenReturn(List.of(
                new NasdaqSymbol("AAPL", "Apple Inc. - Common Stock", NasdaqSymbolType.EQUITY, "NASDAQ")));

        mockMvc.perform(get("/api/us/nasdaq/symbols")
                        .param("query", "appl")
                        .param("type", "EQUITY")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc. - Common Stock"))
                .andExpect(jsonPath("$[0].type").value("EQUITY"))
                .andExpect(jsonPath("$[0].exchange").value("NASDAQ"));
    }

    @Test
    void listWithoutParametersReturnsEverything() throws Exception {
        when(nasdaqSymbolService.search(null, null, null)).thenReturn(List.of(
                new NasdaqSymbol("SPY", "State Street SPDR S&P 500 ETF Trust", NasdaqSymbolType.ETF, "NYSE Arca")));

        mockMvc.perform(get("/api/us/nasdaq/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("ETF"))
                .andExpect(jsonPath("$[0].exchange").value("NYSE Arca"));
    }

    @Test
    void invalidTypeOrLimitIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/us/nasdaq/symbols").param("type", "BOND")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/us/nasdaq/symbols").param("limit", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/us/nasdaq/symbols").param("limit", "20001")).andExpect(status().isBadRequest());
        verifyNoInteractions(nasdaqSymbolService);
    }

    @Test
    void searchBeforeSyncIsServiceUnavailable() throws Exception {
        when(nasdaqSymbolService.search("aapl", null, null))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "not downloaded"));

        mockMvc.perform(get("/api/us/nasdaq/symbols").param("query", "aapl"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void syncReturnsCounts() throws Exception {
        when(nasdaqSymbolService.refreshSymbolLists())
                .thenReturn(new NasdaqSymbolService.SyncResult(9000, 4200, Instant.parse("2026-11-21T11:00:00Z")));

        mockMvc.perform(post("/api/us/nasdaq/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equities").value(9000))
                .andExpect(jsonPath("$.etfs").value(4200));
        verify(nasdaqSymbolService).refreshSymbolLists();
    }
}
