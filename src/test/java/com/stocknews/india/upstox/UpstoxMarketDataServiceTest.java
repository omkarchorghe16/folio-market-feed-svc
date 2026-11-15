package com.stocknews.india.upstox;

import com.stocknews.india.upstox.UpstoxMarketDataClient.UpstoxCandle;
import com.stocknews.india.upstox.UpstoxMarketDataClient.UpstoxQuote;
import com.stocknews.model.UpstoxStockCandle;
import com.stocknews.model.UpstoxStockQuote;
import com.stocknews.repository.UpstoxStockCandleRepository;
import com.stocknews.repository.UpstoxStockQuoteRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpstoxMarketDataServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), ZoneId.of("America/Chicago"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private UpstoxMarketDataClient client;
    @Mock
    private UpstoxStockQuoteRepository quoteRepository;
    @Mock
    private UpstoxStockCandleRepository candleRepository;

    private UpstoxMarketDataService service;

    @BeforeEach
    void setUp() {
        service = new UpstoxMarketDataService(client, quoteRepository, candleRepository, CLOCK);
    }

    @Test
    void savesQuotesAndKeepsCreatedDateOnUpdate() {
        UpstoxStockQuote existing = new UpstoxStockQuote();
        existing.setId(7L);
        existing.setSymbol("TCS");
        existing.setCreatedDate(NOW.minusDays(5));
        when(quoteRepository.findAllByExchangeAndSymbolIn("NSE", List.of("RELIANCE", "TCS"))).thenReturn(List.of(existing));
        when(client.quotes(List.of("RELIANCE", "TCS"), "NSE")).thenReturn(List.of(quote("RELIANCE"), quote("TCS")));
        when(quoteRepository.saveAndFlush(any(UpstoxStockQuote.class))).thenAnswer(call -> call.getArgument(0));

        List<UpstoxStockQuote> result = service.quotes(List.of("reliance", "TCS", "bad sym!"), "nse");

        assertEquals(List.of("RELIANCE", "TCS"), result.stream().map(UpstoxStockQuote::getSymbol).toList());
        assertEquals(NOW, result.get(0).getCreatedDate());
        assertEquals(NOW.minusDays(5), result.get(1).getCreatedDate());
        assertEquals(NOW, result.get(1).getUpdatedDate());
    }

    @Test
    void upstoxFailureIsLoggedAndReturnsSavedQuotes() {
        UpstoxStockQuote saved = new UpstoxStockQuote();
        saved.setSymbol("RELIANCE");
        when(quoteRepository.findAllByExchangeAndSymbolIn("NSE", List.of("RELIANCE"))).thenReturn(List.of(saved));
        when(client.quotes(anyList(), eq("NSE")))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "token expired"));

        assertEquals(List.of(saved), service.quotes(List.of("RELIANCE"), "NSE"));
    }

    @Test
    void invalidExchangeIsLoggedAndReturnsEmpty() {
        assertTrue(service.quotes(List.of("RELIANCE"), "NYSE").isEmpty());
        verify(client, never()).quotes(anyList(), anyString());
    }

    @Test
    void upsertsCandlesMatchingExistingRowsByInstant() {
        // Saved row came back from PostgreSQL in UTC; Upstox sends the same instant in +05:30.
        UpstoxStockCandle existing = new UpstoxStockCandle();
        existing.setId(1L);
        existing.setCandleTime(OffsetDateTime.parse("2026-11-12T00:00:00+05:30").withOffsetSameInstant(ZoneOffset.UTC));
        existing.setCreatedDate(NOW.minusDays(1));
        when(candleRepository.findAllBySymbolAndExchangeAndUnitAndIntervalAndCandleTimeBetweenOrderByCandleTimeDesc(
                eq("RELIANCE"), eq("NSE"), eq("days"), eq(1), any(), any())).thenReturn(List.of(existing));
        when(client.candles("RELIANCE", "NSE", "days", 1, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13)))
                .thenReturn(List.of(candle("2026-11-13T00:00:00+05:30"), candle("2026-11-12T00:00:00+05:30")));
        when(candleRepository.saveAllAndFlush(anyList())).thenAnswer(call -> call.getArgument(0));

        List<UpstoxStockCandle> result = service.candles("RELIANCE", "NSE", "days", 1, "2026-11-10", "2026-11-13");

        assertEquals(2, result.size());
        assertSame(existing, result.get(1));
        assertEquals(NOW.minusDays(1), existing.getCreatedDate());
        assertEquals(NOW, existing.getUpdatedDate());
        assertEquals(NOW, result.get(0).getCreatedDate());
    }

    @Test
    void badDateOrUnitIsLoggedAndReturnsEmpty() {
        assertTrue(service.candles("RELIANCE", "NSE", "days", 1, "13-11-2026", null).isEmpty());
        assertTrue(service.candles("RELIANCE", "NSE", "seconds", 1, null, null).isEmpty());
        assertTrue(service.candles("RELIANCE", "NSE", "days", 1, "2026-11-13", "2026-11-01").isEmpty());
        verify(client, never()).candles(anyString(), anyString(), anyString(), anyInt(), any(), any());
    }

    @Test
    void unknownSymbolFromUpstoxIsLoggedAndReturnsSavedCandles() {
        when(candleRepository.findAllBySymbolAndExchangeAndUnitAndIntervalAndCandleTimeBetweenOrderByCandleTimeDesc(
                anyString(), anyString(), anyString(), anyInt(), any(), any())).thenReturn(List.of());
        when(client.candles(eq("NOSUCH"), eq("NSE"), eq("days"), eq(1), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "NOSUCH is not an NSE equity symbol"));

        assertTrue(service.candles("NOSUCH", "NSE", "days", 1, null, null).isEmpty());
        verify(candleRepository, never()).saveAllAndFlush(anyList());
    }

    private static UpstoxQuote quote(String symbol) {
        return new UpstoxQuote(symbol, "NSE", symbol + " LTD", "INE000000000", "NSE_EQ|INE000000000",
                new BigDecimal("100"), null, null, null, null, null, 10L, null, null, null, "2026-11-15T06:56:24+05:30");
    }

    private static UpstoxCandle candle(String time) {
        return new UpstoxCandle(time, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, 100L, 0L);
    }
}
