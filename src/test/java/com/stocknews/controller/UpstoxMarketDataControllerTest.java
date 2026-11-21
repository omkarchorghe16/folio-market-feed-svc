package com.stocknews.controller;

import com.stocknews.india.upstox.UpstoxMarketDataService;
import com.stocknews.model.UpstoxStockCandle;
import com.stocknews.model.UpstoxStockQuote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UpstoxMarketDataController.class)
class UpstoxMarketDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UpstoxMarketDataService upstoxMarketDataService;

    @Test
    void getQuotesDefaultsToNse() throws Exception {
        UpstoxStockQuote quote = new UpstoxStockQuote();
        quote.setSymbol("RELIANCE");
        quote.setExchange("NSE");
        when(upstoxMarketDataService.getQuotes(List.of("RELIANCE"), "NSE")).thenReturn(List.of(quote));

        mockMvc.perform(get("/api/india/upstox/quotes").param("symbols", "RELIANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].exchange").value("NSE"));
    }

    @Test
    void getCandlesUsesDailyDefaults() throws Exception {
        UpstoxStockCandle candle = new UpstoxStockCandle();
        candle.setSymbol("TCS");
        when(upstoxMarketDataService.getCandles("TCS", "NSE", "days", 1, null, null)).thenReturn(List.of(candle));

        mockMvc.perform(get("/api/india/upstox/candles/TCS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("TCS"));
    }

    @Test
    void getCandlesSkipsInvalidIntervalWithoutCallingUpstox() throws Exception {
        mockMvc.perform(get("/api/india/upstox/candles/TCS").param("interval", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(upstoxMarketDataService, never())
                .getCandles(anyString(), anyString(), anyString(), anyInt(), any(), any());
    }
}
