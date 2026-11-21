package com.stocknews.us.nasdaq;

import com.stocknews.config.NasdaqProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Downloads Nasdaq Trader's public symbol-directory files. No API key is needed. */
@Component
public class NasdaqSymbolListClient {
    private final RestClient restClient;
    private final NasdaqProperties nasdaqProperties;

    public NasdaqSymbolListClient(@Qualifier("nasdaqRestClient") RestClient restClient,
                                  NasdaqProperties nasdaqProperties) {
        this.restClient = restClient;
        this.nasdaqProperties = nasdaqProperties;
    }

    public String fetchSymbolList(NasdaqSymbolFile file) throws IOException {
        String url = switch (file) {
            case NASDAQ_LISTED -> nasdaqProperties.getNasdaqListedUrl();
            case OTHER_LISTED -> nasdaqProperties.getOtherListedUrl();
        };
        try {
            byte[] body = restClient.get().uri(url)
                    .header(HttpHeaders.ACCEPT, "text/plain,*/*")
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new IOException("HTTP " + response.getStatusCode().value()
                                    + " downloading Nasdaq " + file + " list");
                        }
                        return response.getBody().readAllBytes();
                    });
            if (body == null || body.length == 0) {
                throw new IOException("Empty response downloading Nasdaq " + file + " list");
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (RestClientException exception) {
            throw new IOException("Could not download Nasdaq " + file + " list: " + exception.getMessage(), exception);
        }
    }
}
