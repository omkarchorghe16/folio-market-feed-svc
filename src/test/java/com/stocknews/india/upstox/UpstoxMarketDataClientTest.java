package com.stocknews.india.upstox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocknews.config.UpstoxProperties;
import com.stocknews.india.upstox.UpstoxMarketDataClient.UpstoxCandle;
import com.stocknews.india.upstox.UpstoxMarketDataClient.UpstoxQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UpstoxMarketDataClientTest {
    private static final String INSTRUMENTS = """
            [
             {"segment":"NSE_EQ","name":"RELIANCE INDUSTRIES LTD","exchange":"NSE","isin":"INE002A01018",
              "instrument_type":"EQ","instrument_key":"NSE_EQ|INE002A01018","trading_symbol":"RELIANCE"},
             {"segment":"NSE_EQ","name":"TATA CONSULTANCY SERV LT","exchange":"NSE","isin":"INE467B01029",
              "instrument_type":"EQ","instrument_key":"NSE_EQ|INE467B01029","trading_symbol":"TCS"},
             {"segment":"NSE_FO","name":"RELIANCE","exchange":"NSE","instrument_type":"FUT",
              "instrument_key":"NSE_FO|12345","trading_symbol":"RELIANCE FUT 27 NOV 26"}
            ]
            """;

    private MockRestServiceServer server;
    private UpstoxMarketDataClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        UpstoxProperties properties = new UpstoxProperties();
        properties.setAccessToken("analytics-token");
        ObjectMapper mapper = new ObjectMapper();
        client = new UpstoxMarketDataClient(restClient, properties,
                new UpstoxInstrumentRegistry(restClient, properties, mapper), mapper);
    }

    @Test
    void quotesKnownSymbolsAndSkipsUnknownOnes() throws IOException {
        expectInstruments();
        server.expect(requestTo("https://api.upstox.com/v2/market-quote/quotes"
                        + "?instrument_key=NSE_EQ%7CINE467B01029,NSE_EQ%7CINE002A01018"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer analytics-token"))
                .andRespond(withSuccess("""
                        {"status":"success","data":{
                          "NSE_EQ:RELIANCE":{"instrument_token":"NSE_EQ|INE002A01018","last_price":1310.5,
                            "ohlc":{"open":1300,"high":1315,"low":1298,"close":1302},"net_change":8.5,"volume":5000000},
                          "NSE_EQ:TCS":{"instrument_token":"NSE_EQ|INE467B01029","last_price":3200.0,
                            "ohlc":{"open":3190,"high":3210,"low":3180,"close":3195},"volume":1200000}}}
                        """, MediaType.APPLICATION_JSON));

        List<UpstoxQuote> quotes = client.quotes(List.of("tcs", "NOSUCH", "RELIANCE"), "nse");

        assertEquals(List.of("TCS", "RELIANCE"), quotes.stream().map(UpstoxQuote::symbol).toList());
        assertEquals(0, quotes.get(1).lastPrice().compareTo(new java.math.BigDecimal("1310.5")));
        assertEquals("INE002A01018", quotes.get(1).isin());
        server.verify();
    }

    @Test
    void fetchesCandlesWithEncodedInstrumentKeyAndMapsArrayOrder() throws IOException {
        expectInstruments();
        server.expect(requestTo("https://api.upstox.com/v3/historical-candle/NSE_EQ%7CINE002A01018/days/1/2026-11-13/2026-11-01"))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"candles":[
                          ["2026-11-13T00:00:00+05:30",1300.0,1315.5,1298.0,1310.5,5000000,0]]}}
                        """, MediaType.APPLICATION_JSON));

        List<UpstoxCandle> candles = client.candles("RELIANCE", "NSE", "days", 1,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 13));

        assertEquals(1, candles.size());
        assertEquals(0, candles.getFirst().high().compareTo(new java.math.BigDecimal("1315.5")));
        assertEquals(5000000L, candles.getFirst().volume());
        server.verify();
    }

    @Test
    void reportsExpiredTokenAs503() throws IOException {
        expectInstruments();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.upstox.com/v2/market-quote/quotes")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> client.quotes(List.of("RELIANCE"), "NSE"));

        assertEquals(503, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("Analytics Token"));
    }

    @Test
    void rejectsUnknownCandleUnit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> client.candles("RELIANCE", "NSE", "seconds", 1, null, LocalDate.now()));

        assertEquals(400, exception.getStatusCode().value());
    }

    private void expectInstruments() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(INSTRUMENTS.getBytes(StandardCharsets.UTF_8));
        }
        server.expect(requestTo("https://assets.upstox.com/market-quote/instruments/exchange/NSE.json.gz"))
                .andRespond(withSuccess(bytes.toByteArray(), MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void parserKeepsOnlyEquitySegment() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(INSTRUMENTS.getBytes(StandardCharsets.UTF_8));
        }
        UpstoxInstrumentRegistry registry = new UpstoxInstrumentRegistry(RestClient.create(),
                new UpstoxProperties(), new ObjectMapper());

        var parsed = registry.parse(new ByteArrayInputStream(bytes.toByteArray()), "NSE_EQ");

        assertEquals(2, parsed.size());
        assertEquals("NSE_EQ|INE467B01029", parsed.get("TCS").instrumentKey());
    }
}
