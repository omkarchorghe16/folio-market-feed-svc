package com.stocknews.india.tapetide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocknews.config.TapetideProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TapetideClientTest {
    private static final String BASE = "https://mcp.tapetide.com";

    private MockRestServiceServer server;
    private TapetideClient client;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-14T10:00:00Z"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TapetideProperties properties = new TapetideProperties();
        properties.setToken("tpt_rt_test");
        client = new TapetideClient(builder.build(), properties, new ObjectMapper(), clock);
    }

    @Test
    void exchangesRefreshTokenInitializesAndReturnsStructuredToolResult() {
        expectToken("access-1");
        expectInitialize("access-1");
        server.expect(requestTo(BASE + "/mcp"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-1"))
                .andExpect(header("Mcp-Session-Id", "session-1"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andExpect(jsonPath("$.params.name").value("get_stock_quote"))
                .andExpect(jsonPath("$.params.arguments.symbol").value("RELIANCE"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"ignored"}],
                         "structuredContent":{"symbol":"RELIANCE","pe":24.1}}}
                        """, MediaType.APPLICATION_JSON));

        JsonNode result = client.callTool("get_stock_quote", Map.of("symbol", "RELIANCE"));

        assertEquals(24.1, result.path("pe").asDouble());
        server.verify();
    }

    @Test
    void parsesJsonTextContentFromServerSentEvents() {
        expectToken("access-1");
        expectInitialize("access-1");
        server.expect(ExpectedCount.once(), requestTo(BASE + "/mcp"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andRespond(withSuccess("""
                        event: message
                        data: {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\\"eps\\":51.2}"}]}}

                        """, MediaType.TEXT_EVENT_STREAM));

        JsonNode result = client.callTool("get_forecasts", Map.of("symbol", "TCS"));

        assertEquals(51.2, result.path("eps").asDouble());
        server.verify();
    }

    @Test
    void refreshesTokenOnceWhenToolCallReturns401() {
        expectToken("access-1");
        expectInitialize("access-1");
        server.expect(requestTo(BASE + "/mcp"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("access-2");
        expectInitialize("access-2");
        server.expect(requestTo(BASE + "/mcp"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-2"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":5,"result":{"structuredContent":{"ok":true}}}
                        """, MediaType.APPLICATION_JSON));

        JsonNode result = client.callTool("get_shareholding", Map.of("symbol", "INFY"));

        assertEquals(true, result.path("ok").asBoolean());
        server.verify();
    }

    @Test
    void mapsToolNotFoundErrorTo404() {
        expectToken("access-1");
        expectInitialize("access-1");
        server.expect(requestTo(BASE + "/mcp"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":3,"result":{"isError":true,
                         "content":[{"type":"text","text":"Symbol FOO not found"}]}}
                        """, MediaType.APPLICATION_JSON));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> client.callTool("get_stock_quote", Map.of("symbol", "FOO")));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void mapsRateLimitTo429() {
        expectToken("access-1");
        expectInitialize("access-1");
        server.expect(requestTo(BASE + "/mcp"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> client.callTool("get_stock_quote", Map.of("symbol", "TCS")));

        assertEquals(429, exception.getStatusCode().value());
    }

    @Test
    void rejectsCallsWhenTokenIsMissing() {
        TapetideClient unconfigured = new TapetideClient(RestClient.create(), new TapetideProperties(),
                new ObjectMapper());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> unconfigured.callTool("get_stock_quote", Map.of("symbol", "TCS")));

        assertEquals(503, exception.getStatusCode().value());
    }

    @Test
    void pausesAfterPlanExpiredToolErrorAndMakesNoCallsUntilThePauseEnds() {
        expectToken("access-1");
        expectInitialize("access-1");
        server.expect(requestTo(BASE + "/mcp"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":3,"result":{"isError":true,"content":[{"type":"text",
                         "text":"Your free Tapetide access is now over. To keep using Tapetide here, you need to upgrade your plan."}]}}
                        """, MediaType.APPLICATION_JSON));

        ResponseStatusException first = assertThrows(ResponseStatusException.class,
                () -> client.callTool("get_stock_overview", Map.of("symbol", "TCS")));
        assertEquals(429, first.getStatusCode().value());

        // No server expectation is left, so any network call here would fail the test.
        assertThrows(TapetidePausedException.class,
                () -> client.callTool("get_stock_overview", Map.of("symbol", "INFY")));
        TapetideAvailability availability = client.availability();
        assertFalse(availability.available());
        assertEquals(clock.instant().plus(Duration.ofMinutes(180)), availability.pausedUntil());
        assertTrue(availability.reason().contains("access is now over"));
        server.verify();

        clock.advance(Duration.ofMinutes(181));
        assertTrue(client.availability().available());
    }

    @Test
    void pausesForRetryAfterOnRateLimit() {
        expectToken("access-1");
        expectInitialize("access-1");
        server.expect(requestTo(BASE + "/mcp"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "600"));

        assertThrows(ResponseStatusException.class,
                () -> client.callTool("get_stock_quote", Map.of("symbol", "TCS")));

        assertEquals(clock.instant().plusSeconds(600), client.availability().pausedUntil());
    }

    @Test
    void unknownSymbolDoesNotPauseCalls() {
        expectToken("access-1");
        expectInitialize("access-1");
        server.expect(requestTo(BASE + "/mcp"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":3,"result":{"isError":true,
                         "content":[{"type":"text","text":"Symbol FOO not found"}]}}
                        """, MediaType.APPLICATION_JSON));

        assertThrows(ResponseStatusException.class, () -> client.callTool("get_stock_quote", Map.of("symbol", "FOO")));

        assertTrue(client.availability().available());
    }

    private void expectToken(String accessToken) {
        server.expect(requestTo(BASE + "/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("grant_type", "refresh_token",
                        "refresh_token", "tpt_rt_test")))
                .andRespond(withSuccess("{\"access_token\":\"" + accessToken + "\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectInitialize(String accessToken) {
        server.expect(requestTo(BASE + "/mcp"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(jsonPath("$.method").value("initialize"))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}", MediaType.APPLICATION_JSON)
                        .header("Mcp-Session-Id", "session-1"));
        server.expect(requestTo(BASE + "/mcp"))
                .andExpect(jsonPath("$.method").value("notifications/initialized"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
    }

    /** A clock the test can move forward, to check when a pause ends. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
