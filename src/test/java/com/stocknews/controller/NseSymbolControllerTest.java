package com.stocknews.controller;

import com.stocknews.india.nse.NseSymbol;
import com.stocknews.india.nse.NseSymbolService;
import com.stocknews.india.nse.NseSymbolType;
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

@WebMvcTest(NseSymbolController.class)
class NseSymbolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NseSymbolService nseSymbolService;

    @Test
    void searchReturnsMatchingSymbols() throws Exception {
        when(nseSymbolService.search("reli", NseSymbolType.EQUITY, 20)).thenReturn(List.of(
                new NseSymbol("RELIANCE", "Reliance Industries Limited", NseSymbolType.EQUITY, "EQ", "INE002A01018")));

        mockMvc.perform(get("/api/india/nse/symbols").param("query", "reli").param("type", "EQUITY").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].name").value("Reliance Industries Limited"))
                .andExpect(jsonPath("$[0].type").value("EQUITY"));
    }

    @Test
    void listWithoutParametersReturnsEverything() throws Exception {
        when(nseSymbolService.search(null, null, null)).thenReturn(List.of(
                new NseSymbol("NIFTYBEES", "Nippon India ETF Nifty 50 BeES", NseSymbolType.ETF, null, "INF204KB14I2")));

        mockMvc.perform(get("/api/india/nse/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("ETF"));
    }

    @Test
    void invalidTypeOrLimitIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/india/nse/symbols").param("type", "BOND")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/india/nse/symbols").param("limit", "0")).andExpect(status().isBadRequest());
        verifyNoInteractions(nseSymbolService);
    }

    @Test
    void searchBeforeSyncIsServiceUnavailable() throws Exception {
        when(nseSymbolService.search("tcs", null, null))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "not downloaded"));

        mockMvc.perform(get("/api/india/nse/symbols").param("query", "tcs"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void syncReturnsCounts() throws Exception {
        when(nseSymbolService.refreshSymbolLists()).thenReturn(new NseSymbolService.SyncResult(2300, 250, Instant.parse("2026-11-22T00:30:00Z")));

        mockMvc.perform(post("/api/india/nse/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equities").value(2300))
                .andExpect(jsonPath("$.etfs").value(250));
        verify(nseSymbolService).refreshSymbolLists();
    }
}
