package com.stocknews.india.nse;

import com.stocknews.config.NseProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Downloads NSE's public symbol-list CSV files. No API key is needed. */
@Component
public class NseSymbolListClient {
    private final RestClient restClient;
    private final NseProperties properties;

    public NseSymbolListClient(@Qualifier("nseRestClient") RestClient restClient, NseProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public String download(NseSymbolType type) throws IOException {
        String url = type == NseSymbolType.ETF ? properties.getEtfListUrl() : properties.getEquityListUrl();
        try {
            byte[] body = restClient.get().uri(url)
                    .header(HttpHeaders.USER_AGENT, properties.getUserAgent())
                    .header(HttpHeaders.ACCEPT, "text/csv,*/*")
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new IOException("HTTP " + response.getStatusCode().value()
                                    + " downloading NSE " + type + " list");
                        }
                        return response.getBody().readAllBytes();
                    });
            if (body == null || body.length == 0) {
                throw new IOException("Empty response downloading NSE " + type + " list");
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (RestClientException exception) {
            throw new IOException("Could not download NSE " + type + " list: " + exception.getMessage(), exception);
        }
    }
}
