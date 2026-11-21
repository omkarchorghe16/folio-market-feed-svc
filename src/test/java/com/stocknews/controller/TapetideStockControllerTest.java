package com.stocknews.controller;

import com.stocknews.india.tapetide.TapetideStockService;
import com.stocknews.model.TapetideStockQuote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TapetideStockController.class)
class TapetideStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TapetideStockService tapetideStockService;

    @Test
    void getQuoteReturnsSavedQuote() throws Exception {
        TapetideStockQuote quote = new TapetideStockQuote();
        quote.setSymbol("RELIANCE");
        quote.setPrice(new BigDecimal("2950.50"));
        when(tapetideStockService.getQuote("RELIANCE")).thenReturn(Optional.of(quote));

        mockMvc.perform(get("/api/india/tapetide/quote/RELIANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.price").value(2950.50));
    }

    @Test
    void getQuoteReturnsNoContentWhenNothingIsSaved() throws Exception {
        when(tapetideStockService.getQuote("NOSUCH")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/india/tapetide/quote/NOSUCH"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getQuotesAcceptsCommaSeparatedSymbols() throws Exception {
        TapetideStockQuote quote = new TapetideStockQuote();
        quote.setSymbol("TCS");
        when(tapetideStockService.getQuotes(List.of("RELIANCE", "TCS"))).thenReturn(List.of(quote));

        mockMvc.perform(get("/api/india/tapetide/quotes").param("symbols", "RELIANCE,TCS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("TCS"));
    }

    @Test
    void getFinancialsIgnoresInvalidPeriodsInsteadOfFailing() throws Exception {
        when(tapetideStockService.getFinancials("TCS", "ratios", null)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/india/tapetide/financials/TCS")
                        .param("section", "ratios")
                        .param("periods", "abc"))
                .andExpect(status().isNoContent());

        verify(tapetideStockService).getFinancials("TCS", "ratios", null);
    }
}
