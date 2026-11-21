package com.stocknews.us.nasdaq;

import com.stocknews.config.NasdaqProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NasdaqSymbolListClientTest {
    private MockRestServiceServer server;
    private NasdaqSymbolListClient nasdaqSymbolListClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        NasdaqProperties nasdaqProperties = new NasdaqProperties();
        nasdaqProperties.setNasdaqListedUrl("https://nasdaq.test/nasdaqlisted.txt");
        nasdaqProperties.setOtherListedUrl("https://nasdaq.test/otherlisted.txt");
        nasdaqSymbolListClient = new NasdaqSymbolListClient(builder.build(), nasdaqProperties);
    }

    @Test
    void downloadsEachFileFromItsUrl() throws IOException {
        server.expect(requestTo("https://nasdaq.test/nasdaqlisted.txt"))
                .andRespond(withSuccess(NasdaqSymbolFixtures.NASDAQ_LISTED, MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://nasdaq.test/otherlisted.txt"))
                .andRespond(withSuccess(NasdaqSymbolFixtures.OTHER_LISTED, MediaType.TEXT_PLAIN));

        assertEquals(NasdaqSymbolFixtures.NASDAQ_LISTED,
                nasdaqSymbolListClient.fetchSymbolList(NasdaqSymbolFile.NASDAQ_LISTED));
        assertEquals(NasdaqSymbolFixtures.OTHER_LISTED,
                nasdaqSymbolListClient.fetchSymbolList(NasdaqSymbolFile.OTHER_LISTED));
        server.verify();
    }

    @Test
    void httpErrorAndEmptyBodyAreFailures() {
        server.expect(requestTo("https://nasdaq.test/nasdaqlisted.txt"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://nasdaq.test/otherlisted.txt"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThrows(IOException.class, () -> nasdaqSymbolListClient.fetchSymbolList(NasdaqSymbolFile.NASDAQ_LISTED));
        assertThrows(IOException.class, () -> nasdaqSymbolListClient.fetchSymbolList(NasdaqSymbolFile.OTHER_LISTED));
    }
}
