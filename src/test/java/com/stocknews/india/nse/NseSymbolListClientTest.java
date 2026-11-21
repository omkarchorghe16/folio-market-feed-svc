package com.stocknews.india.nse;

import com.stocknews.config.NseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NseSymbolListClientTest {
    private MockRestServiceServer server;
    private NseSymbolListClient client;
    private NseProperties properties;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new NseProperties();
        properties.setEquityListUrl("https://nse.test/EQUITY_L.csv");
        properties.setEtfListUrl("https://nse.test/eq_etfseclist.csv");
        client = new NseSymbolListClient(builder.build(), properties);
    }

    @Test
    void downloadsEquityListWithBrowserUserAgent() throws IOException {
        server.expect(requestTo("https://nse.test/EQUITY_L.csv"))
                .andExpect(header(HttpHeaders.USER_AGENT, properties.getUserAgent()))
                .andRespond(withSuccess(NseSymbolFixtures.EQUITY_CSV, MediaType.TEXT_PLAIN));

        assertEquals(NseSymbolFixtures.EQUITY_CSV, client.download(NseSymbolType.EQUITY));
        server.verify();
    }

    @Test
    void downloadsEtfListFromEtfUrl() throws IOException {
        server.expect(requestTo("https://nse.test/eq_etfseclist.csv"))
                .andRespond(withSuccess(NseSymbolFixtures.ETF_CSV, MediaType.TEXT_PLAIN));

        assertEquals(NseSymbolFixtures.ETF_CSV, client.download(NseSymbolType.ETF));
    }

    @Test
    void httpErrorAndEmptyBodyAreFailures() {
        server.expect(requestTo("https://nse.test/EQUITY_L.csv")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("https://nse.test/eq_etfseclist.csv")).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThrows(IOException.class, () -> client.download(NseSymbolType.EQUITY));
        assertThrows(IOException.class, () -> client.download(NseSymbolType.ETF));
    }
}
