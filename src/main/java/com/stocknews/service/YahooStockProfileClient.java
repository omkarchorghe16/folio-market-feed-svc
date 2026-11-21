package com.stocknews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocknews.config.AppProperties;
import com.stocknews.logging.ExceptionLog;
import com.stocknews.model.YahooStockProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class YahooStockProfileClient {
    private final RestClient restClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Safari/537.36";
    private static final List<String> COOKIE_SOURCES = List.of("https://fc.yahoo.com", "https://finance.yahoo.com");

    /** Yahoo requires a session cookie plus a matching crumb for quoteSummary calls. */
    private record YahooSession(String cookie, String crumb) {
    }

    private volatile YahooSession session;

    public List<YahooStockProfile> fetchProfiles(List<String> symbols) {
        List<YahooStockProfile> profiles = new ArrayList<>();
        for (String symbol : symbols) {
            try {
                String response;
                try {
                    response = requestProfile(symbol, getSession(false));
                } catch (HttpClientErrorException.Unauthorized unauthorized) {
                    log.warn("Yahoo Finance rejected crumb for {}; refreshing session and retrying", symbol);
                    response = requestProfile(symbol, getSession(true));
                }
                profiles.add(parseProfile(symbol, response));
            } catch (RestClientException exception) {
                log.error("Yahoo Finance profile request failed for symbol {}:\n{}",
                        symbol, ExceptionLog.stackTrace(exception));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Yahoo Finance profile lookup failed for " + symbol, exception);
            }
        }
        return profiles;
    }

    private String requestProfile(String symbol, YahooSession yahooSession) {
        return restClient.get()
                .uri(UriComponentsBuilder.fromUriString(appProperties.getApis().getYahooBaseUrl())
                        .pathSegment("v10", "finance", "quoteSummary", symbol)
                        .queryParam("modules", "assetProfile,summaryProfile,summaryDetail,price")
                        .queryParam("crumb", yahooSession.crumb())
                        .build()
                        .encode()
                        .toUri())
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.COOKIE, yahooSession.cookie())
                .retrieve()
                .body(String.class);
    }

    private synchronized YahooSession getSession(boolean forceRefresh) {
        if (session != null && !forceRefresh) {
            return session;
        }
        String cookie = fetchCookie();
        String crumb = restClient.get()
                .uri(UriComponentsBuilder.fromUriString(appProperties.getApis().getYahooBaseUrl())
                        .pathSegment("v1", "test", "getcrumb")
                        .build()
                        .toUri())
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.COOKIE, cookie)
                .retrieve()
                .body(String.class);
        if (crumb == null || crumb.isBlank() || crumb.contains("<")) {
            throw new RestClientException("Yahoo Finance returned an invalid crumb");
        }
        session = new YahooSession(cookie, crumb.trim());
        log.info("Obtained new Yahoo Finance session crumb");
        return session;
    }

    private String fetchCookie() {
        for (String source : COOKIE_SOURCES) {
            try {
                // fc.yahoo.com answers 404 but still sets the session cookie, so read headers without status handling.
                List<String> setCookies = restClient.get()
                        .uri(source)
                        .header(HttpHeaders.USER_AGENT, USER_AGENT)
                        .exchange((request, response) -> response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
                String cookie = setCookies.stream()
                        .map(value -> value.split(";", 2)[0].trim())
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.joining("; "));
                if (!cookie.isEmpty()) {
                    return cookie;
                }
            } catch (RestClientException exception) {
                log.warn("Could not obtain Yahoo Finance cookie from {}: {}", source, exception.getMessage());
            }
        }
        throw new RestClientException("Could not obtain a Yahoo Finance session cookie");
    }

    private YahooStockProfile parseProfile(String symbol, String response) {
        try {
            JsonNode result = objectMapper.readTree(response)
                    .path("quoteSummary")
                    .path("result")
                    .path(0);
            if (result.isMissingNode() || result.isNull()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Yahoo Finance returned no profile for " + symbol);
            }

            JsonNode asset = result.path("assetProfile");
            JsonNode summary = result.path("summaryProfile");
            JsonNode price = result.path("price");
            JsonNode summaryDetail = result.path("summaryDetail");
            YahooStockProfile profile = new YahooStockProfile();
            profile.setSymbol(symbol);
            profile.setName(text(price, "longName", text(price, "shortName", null)));
            profile.setCountry(text(asset, "country", text(summary, "country", null)));
            profile.setCity(text(asset, "city", text(summary, "city", null)));
            profile.setState(text(asset, "state", text(summary, "state", null)));
            profile.setAddress(text(asset, "address1", text(summary, "address1", null)));
            profile.setPostalCode(text(asset, "zip", text(summary, "zip", null)));
            profile.setPhone(text(asset, "phone", text(summary, "phone", null)));
            profile.setWebsite(text(asset, "website", text(summary, "website", null)));
            profile.setSector(text(asset, "sector", text(summary, "sector", null)));
            profile.setIndustry(text(asset, "industry", text(summary, "industry", null)));
            profile.setFullTimeEmployees(longValue(asset, "fullTimeEmployees"));
            profile.setCurrency(text(price, "currency", null));
            profile.setMarketCapitalization(decimal(price.path("marketCap"), decimal(summaryDetail.path("marketCap"), null)));
            profile.setBusinessSummary(text(asset, "longBusinessSummary", text(summary, "longBusinessSummary", null)));
            profile.setRawPayload(response);
            return profile;
        } catch (ResponseStatusException exception) {
            log.error("Yahoo Finance profile processing failed for symbol {}:\n{}",
                    symbol, ExceptionLog.stackTrace(exception));
            throw exception;
        } catch (Exception exception) {
            log.error("Could not parse Yahoo Finance profile for symbol {}:\n{}",
                    symbol, ExceptionLog.stackTrace(exception));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not parse Yahoo Finance profile for " + symbol, exception);
        }
    }

    private String text(JsonNode root, String field, String fallback) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    private Long longValue(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        JsonNode raw = value.isObject() ? value.get("raw") : value;
        return raw == null || !raw.isNumber() ? null : raw.longValue();
    }

    private BigDecimal decimal(JsonNode value, BigDecimal fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        JsonNode raw = value.isObject() ? value.get("raw") : value;
        return raw == null || !raw.isNumber() ? fallback : raw.decimalValue();
    }
}
