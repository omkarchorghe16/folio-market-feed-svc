package com.stocknews.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuration for Spring 6.1+ RestClient with timeout and retry settings.
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {
    private final AppProperties appProperties;

    private final TapetideProperties tapetideProperties;

    private final NseProperties nseProperties;

    private final NasdaqProperties nasdaqProperties;

    @Bean
    @Primary
    public RestClient restClient() {
        return withTimeout(appProperties.getApis().getTimeoutSeconds());
    }

    /** Tapetide tool calls (financial statements, profiles) can take longer than the default API timeout. */
    @Bean
    public RestClient tapetideRestClient() {
        return withTimeout(tapetideProperties.getTimeoutSeconds());
    }

    /** NSE symbol-list CSV downloads (a few hundred KB each). */
    @Bean
    public RestClient nseRestClient() {
        return withTimeout(nseProperties.getTimeoutSeconds());
    }

    /** Nasdaq Trader symbol-directory downloads (about 350 KB and 550 KB). */
    @Bean
    public RestClient nasdaqRestClient() {
        return withTimeout(nasdaqProperties.getTimeoutSeconds());
    }

    private static RestClient withTimeout(int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
