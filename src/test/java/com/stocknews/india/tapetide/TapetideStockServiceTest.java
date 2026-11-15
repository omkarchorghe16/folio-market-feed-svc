package com.stocknews.india.tapetide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocknews.config.TapetideProperties;
import com.stocknews.model.TapetideStockDataset;
import com.stocknews.model.TapetideStockQuote;
import com.stocknews.repository.TapetideStockDatasetRepository;
import com.stocknews.repository.TapetideStockQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TapetideStockServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), ZoneId.of("America/Chicago"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private TapetideClient client;
    @Mock
    private TapetideStockQuoteRepository quoteRepository;
    @Mock
    private TapetideStockDatasetRepository datasetRepository;

    private TapetideStockService service;

    @BeforeEach
    void setUp() {
        service = new TapetideStockService(client, quoteRepository, datasetRepository, new TapetideProperties(),
                MAPPER, CLOCK);
    }

    @Test
    void savesNewQuoteWithPeAndBothDates() throws Exception {
        when(quoteRepository.findBySymbol("RELIANCE")).thenReturn(Optional.empty());
        when(client.callTool("get_stock_quote", Map.of("symbol", "RELIANCE"))).thenReturn(json("""
                {"data":{"found":true,"symbol":"RELIANCE","name":"Reliance Industries Ltd","price":1339.7,
                 "pe_ttm":22.08,"pb":1.92,"high_52w":1611.8,"low_52w":1152.2,"volume":13756247}}"""));
        when(quoteRepository.saveAndFlush(any(TapetideStockQuote.class))).thenAnswer(call -> call.getArgument(0));

        TapetideStockQuote quote = service.quote(" reliance.ns ").orElseThrow();

        assertEquals("RELIANCE", quote.getSymbol());
        assertEquals(0, quote.getPeTtm().compareTo(new BigDecimal("22.08")));
        assertEquals(13756247L, quote.getVolume());
        assertEquals(NOW, quote.getCreatedDate());
        assertEquals(NOW, quote.getUpdatedDate());
    }

    @Test
    void servesFreshQuoteFromDatabaseWithoutCallingTapetide() {
        TapetideStockQuote saved = quote("TCS", NOW.minusMinutes(10));
        when(quoteRepository.findBySymbol("TCS")).thenReturn(Optional.of(saved));

        assertSame(saved, service.quote("TCS").orElseThrow());
        verify(client, never()).callTool(anyString(), anyMap());
    }

    @Test
    void notFoundSymbolIsLoggedAndReturnsEmptyWithoutSaving() throws Exception {
        when(quoteRepository.findBySymbol("NOSUCH")).thenReturn(Optional.empty());
        when(client.callTool(eq("get_stock_quote"), anyMap())).thenReturn(json("{\"data\":{\"found\":false}}"));

        assertTrue(service.quote("NOSUCH").isEmpty());
        verify(quoteRepository, never()).saveAndFlush(any());
    }

    @Test
    void tapetideErrorFallsBackToStaleSavedQuote() {
        TapetideStockQuote stale = quote("INFY", NOW.minusDays(2));
        when(quoteRepository.findBySymbol("INFY")).thenReturn(Optional.of(stale));
        when(client.callTool(eq("get_stock_quote"), anyMap()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limit"));

        assertSame(stale, service.quote("INFY").orElseThrow());
    }

    @Test
    void invalidSymbolIsLoggedAndNeverSentToTapetide() {
        assertTrue(service.quote("RELI ANCE!!").isEmpty());
        verify(client, never()).callTool(anyString(), anyMap());
    }

    @Test
    void batchSkipsUnknownSymbolsAndKeepsPeFromEarlierSingleQuote() throws Exception {
        TapetideStockQuote relianceWithPe = quote("RELIANCE", NOW.minusDays(1));
        relianceWithPe.setPeTtm(new BigDecimal("22.08"));
        when(quoteRepository.findAllBySymbolIn(List.of("RELIANCE", "NOSUCH", "TCS")))
                .thenReturn(List.of(relianceWithPe));
        when(client.callTool(eq("get_batch_quotes"), anyMap())).thenReturn(json("""
                {"data":[{"found":true,"requested":"RELIANCE","symbol":"RELIANCE","price":1340.0},
                         {"found":false,"requested":"NOSUCH"},
                         {"found":true,"requested":"TCS","symbol":"TCS","price":2407.5}]}"""));
        when(quoteRepository.saveAndFlush(any(TapetideStockQuote.class))).thenAnswer(call -> call.getArgument(0));

        List<TapetideStockQuote> quotes = service.quotes(List.of("RELIANCE", "NOSUCH", "TCS", "bad symbol!"));

        assertEquals(List.of("RELIANCE", "TCS"), quotes.stream().map(TapetideStockQuote::getSymbol).toList());
        assertEquals(0, quotes.get(0).getPrice().compareTo(new BigDecimal("1340.0")));
        assertEquals(0, quotes.get(0).getPeTtm().compareTo(new BigDecimal("22.08")));
        verify(quoteRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void savesDatasetKeyedBySymbolDatasetAndOptions() throws Exception {
        when(datasetRepository.findBySymbolAndDatasetAndRequestParams("TCS", "financials", "periods=4;section=ratios"))
                .thenReturn(Optional.empty());
        when(client.callTool(eq("get_financials"), anyMap())).thenReturn(json("{\"data\":{\"ratios\":[{\"roe\":50}]}}"));
        when(datasetRepository.saveAndFlush(any(TapetideStockDataset.class))).thenAnswer(call -> call.getArgument(0));

        TapetideStockDataset row = service.financials("tcs", "RATIOS", 4).orElseThrow();

        assertEquals("financials", row.getDataset());
        assertEquals("periods=4;section=ratios", row.getRequestParams());
        assertTrue(row.getPayload().contains("\"roe\":50"));
        assertEquals(NOW, row.getUpdatedDate());
    }

    @Test
    void emptyDatasetIsLoggedAndNotSaved() throws Exception {
        when(datasetRepository.findBySymbolAndDatasetAndRequestParams("NOSUCH", "forecasts", ""))
                .thenReturn(Optional.empty());
        when(client.callTool(eq("get_forecasts"), anyMap())).thenReturn(json("{\"data\":[],\"meta\":{}}"));

        assertTrue(service.forecasts("NOSUCH").isEmpty());
        verify(datasetRepository, never()).saveAndFlush(any());
    }

    private static TapetideStockQuote quote(String symbol, LocalDateTime updated) {
        TapetideStockQuote quote = new TapetideStockQuote();
        quote.setSymbol(symbol);
        quote.setCreatedDate(updated);
        quote.setUpdatedDate(updated);
        return quote;
    }

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }
}
