package com.stocknews.india.tapetide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stocknews.config.TapetideProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Calls Tapetide tools over its MCP (JSON-RPC over HTTP) endpoint. Tapetide's REST API serves the same
 * datasets with the same token and quota, but its reference is behind a login, while the MCP contract
 * (tool names and arguments) is public and stable.
 *
 * <p>Auth: the personal token ({@code tpt_rt_...}) is a refresh token. It is exchanged at
 * {@code POST /token} for a ~1 hour access token, refreshed shortly before expiry or after a 401.
 */
@Component
@Slf4j
public class TapetideClient {
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);
    private static final String TOKEN_HELP = "Get a free token at https://tapetide.com/settings/tokens and set TAPETIDE_TOKEN";

    private final RestClient restClient;
    private final TapetideProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AtomicLong requestIds = new AtomicLong();

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;
    private String sessionId;
    private boolean initialized;

    private record RawResponse(int status, HttpHeaders headers, String body) {
    }

    @Autowired
    public TapetideClient(@Qualifier("tapetideRestClient") RestClient restClient,
                          TapetideProperties properties, ObjectMapper objectMapper) {
        this(restClient, properties, objectMapper, Clock.systemUTC());
    }

    TapetideClient(RestClient restClient, TapetideProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Calls one Tapetide tool and returns its JSON result. Each call uses one of the free plan's
     * 50 daily calls; {@link TapetideStockService} avoids repeat calls by serving fresh rows from PostgreSQL.
     */
    public synchronized JsonNode callTool(String tool, Map<String, Object> arguments) {
        ObjectNode request = rpc("tools/call");
        request.putObject("params").put("name", tool).set("arguments", objectMapper.valueToTree(arguments));

        RawResponse response = postWithSession(request);
        if (response.status() == HttpStatus.UNAUTHORIZED.value()) {
            log.warn("Tapetide rejected the access token; refreshing and retrying {}", tool);
            resetAuth();
            response = postWithSession(request);
        }
        return toolResult(tool, response);
    }

    private RawResponse postWithSession(ObjectNode request) {
        ensureInitialized();
        return post(request);
    }

    /** MCP handshake; the remote mints an {@code Mcp-Session-Id} that later calls echo back. */
    private void ensureInitialized() {
        if (initialized && accessTokenValid()) {
            return;
        }
        ObjectNode init = rpc("initialize");
        ObjectNode params = init.putObject("params");
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "news-scheduler-svc").put("version", "1.0.0");

        sessionId = null;
        RawResponse response = post(init);
        if (response.status() >= 400) {
            throw failure(response, "Tapetide initialize failed");
        }
        String minted = response.headers().getFirst("Mcp-Session-Id");
        sessionId = minted == null ? null : minted.replaceAll("[^A-Za-z0-9._~+/=-]", "");

        ObjectNode initialized = objectMapper.createObjectNode().put("jsonrpc", "2.0")
                .put("method", "notifications/initialized");
        post(initialized);
        this.initialized = true;
    }

    private RawResponse post(ObjectNode body) {
        String token = accessToken();
        try {
            return restClient.post()
                    .uri(properties.getBaseUrl() + "/mcp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                    .headers(headers -> {
                        if (sessionId != null) {
                            headers.set("Mcp-Session-Id", sessionId);
                        }
                    })
                    .body(objectMapper.writeValueAsString(body))
                    .exchange((req, res) -> new RawResponse(res.getStatusCode().value(), res.getHeaders(),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Tapetide request failed: " + exception.getMessage(), exception);
        }
    }

    private String accessToken() {
        if (accessTokenValid()) {
            return accessToken;
        }
        String refreshToken = properties.getToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Tapetide token is not configured. " + TOKEN_HELP);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        RawResponse response;
        try {
            response = restClient.post()
                    .uri(properties.getBaseUrl() + "/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((req, res) -> new RawResponse(res.getStatusCode().value(), res.getHeaders(),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Tapetide token refresh failed: " + exception.getMessage(), exception);
        }
        if (response.status() >= 400) {
            log.error("Tapetide token refresh failed with HTTP {}: {}", response.status(), abbreviate(response.body()));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Tapetide token was rejected; check TAPETIDE_TOKEN. " + TOKEN_HELP);
        }

        JsonNode json = readJson(response.body());
        accessToken = json.path("access_token").asText(null);
        if (accessToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Tapetide token response had no access_token");
        }
        long expiresIn = Math.max(json.path("expires_in").asLong(3600) - EXPIRY_MARGIN.toSeconds(), 0);
        accessTokenExpiresAt = clock.instant().plusSeconds(expiresIn);
        initialized = false;
        log.info("Obtained Tapetide access token (valid ~{} min)", expiresIn / 60);
        return accessToken;
    }

    private boolean accessTokenValid() {
        return accessToken != null && clock.instant().isBefore(accessTokenExpiresAt);
    }

    private void resetAuth() {
        accessToken = null;
        accessTokenExpiresAt = Instant.EPOCH;
        sessionId = null;
        initialized = false;
    }

    private JsonNode toolResult(String tool, RawResponse response) {
        if (response.status() >= 400) {
            throw failure(response, "Tapetide " + tool + " failed");
        }
        JsonNode message = readJson(extractJsonFromSse(response.body()));
        if (message.has("error")) {
            String error = message.path("error").path("message").asText("unknown error");
            log.error("Tapetide {} returned JSON-RPC error: {}", tool, error);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Tapetide " + tool + " failed: " + error);
        }

        JsonNode result = message.path("result");
        String text = firstText(result);
        if (result.path("isError").asBoolean(false)) {
            log.error("Tapetide {} reported a tool error: {}", tool, abbreviate(text));
            HttpStatus status = text != null && text.toLowerCase().contains("not found")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, "Tapetide " + tool + ": " + abbreviate(text));
        }
        if (result.hasNonNull("structuredContent")) {
            return result.get("structuredContent");
        }
        if (text != null) {
            try {
                return objectMapper.readTree(text);
            } catch (Exception notJson) {
                return objectMapper.createObjectNode().put("text", text);
            }
        }
        return result;
    }

    private ResponseStatusException failure(RawResponse response, String what) {
        if (response.status() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            String retryAfter = response.headers().getFirst(HttpHeaders.RETRY_AFTER);
            log.error("{}: Tapetide rate limit reached (free plan: 50 calls/day); retry after {}", what, retryAfter);
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Tapetide rate limit reached" + (retryAfter == null ? "" : "; retry after " + retryAfter + "s"));
        }
        if (response.status() == HttpStatus.UNAUTHORIZED.value() || response.status() == HttpStatus.FORBIDDEN.value()) {
            log.error("{}: HTTP {} {}", what, response.status(), abbreviate(response.body()));
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Tapetide rejected the credentials. " + TOKEN_HELP);
        }
        log.error("{}: HTTP {} {}", what, response.status(), abbreviate(response.body()));
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, what + " (HTTP " + response.status() + ")");
    }

    private ObjectNode rpc(String method) {
        return objectMapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", requestIds.incrementAndGet())
                .put("method", method);
    }

    private static String firstText(JsonNode result) {
        for (JsonNode item : result.path("content")) {
            if ("text".equals(item.path("type").asText())) {
                return item.path("text").asText();
            }
        }
        return null;
    }

    /** Streamable HTTP may answer as SSE; the JSON-RPC reply is the last {@code data:} line that is JSON. */
    static String extractJsonFromSse(String body) {
        if (body == null || body.stripLeading().startsWith("{")) {
            return body;
        }
        String last = null;
        for (String line : body.split("\n")) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if (data.startsWith("{")) {
                    last = data;
                }
            }
        }
        return last != null ? last : body;
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Tapetide returned a non-JSON response: " + abbreviate(body), exception);
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
