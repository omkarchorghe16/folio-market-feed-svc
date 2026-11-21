package com.stocknews.india.nse;

import com.stocknews.config.NseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NseSymbolServiceTest {
    @TempDir
    Path directory;

    private NseSymbolListClient client;
    private NseSymbolService service;

    @BeforeEach
    void setUp() {
        client = mock(NseSymbolListClient.class);
        NseProperties properties = new NseProperties();
        properties.setStorageDirectory(directory.resolve("nse").toString());
        service = new NseSymbolService(client, properties);
    }

    @Test
    void syncStoresBothCsvFilesAndReportsCounts() throws IOException {
        stubDownloads(NseSymbolFixtures.EQUITY_CSV, NseSymbolFixtures.ETF_CSV);

        NseSymbolService.SyncResult result = service.refreshSymbolLists();

        assertEquals(4, result.equities());
        assertEquals(2, result.etfs());
        assertTrue(service.hasStoredSymbolLists());
        assertEquals(NseSymbolFixtures.ETF_CSV, Files.readString(directory.resolve("nse").resolve(NseSymbolService.ETF_FILE)));
    }

    @Test
    void searchRanksExactAndPrefixMatchesFirst() throws IOException {
        stubDownloads(NseSymbolFixtures.EQUITY_CSV, NseSymbolFixtures.ETF_CSV);
        service.refreshSymbolLists();

        List<String> symbols = service.search("reli", null, null).stream().map(NseSymbol::symbol).toList();

        assertEquals(List.of("RELIANCE", "RELINFRA"), symbols);
        assertEquals("TCS", service.search("tcs", null, null).get(0).symbol());
        assertEquals(List.of("TCS"), service.search("consultancy", null, null).stream().map(NseSymbol::symbol).toList());
    }

    @Test
    void searchFiltersByTypeAndAppliesLimit() throws IOException {
        stubDownloads(NseSymbolFixtures.EQUITY_CSV, NseSymbolFixtures.ETF_CSV);
        service.refreshSymbolLists();

        assertEquals(List.of("GOLDBEES", "NIFTYBEES"),
                service.search(null, NseSymbolType.ETF, null).stream().map(NseSymbol::symbol).toList());
        assertEquals(6, service.search("", null, null).size());
        assertEquals(1, service.search("nippon", NseSymbolType.ETF, 1).size());
        assertTrue(service.search("nippon", NseSymbolType.EQUITY, null).isEmpty());
    }

    @Test
    void searchBeforeFirstSyncIsServiceUnavailable() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.search("reli", null, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void failedDownloadKeepsPreviousFiles() throws IOException {
        stubDownloads(NseSymbolFixtures.EQUITY_CSV, NseSymbolFixtures.ETF_CSV);
        service.refreshSymbolLists();
        when(client.fetchSymbolList(NseSymbolType.EQUITY)).thenThrow(new IOException("HTTP 403"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, service::refreshSymbolLists);

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals(6, service.search(null, null, null).size());
    }

    @Test
    void invalidDownloadIsRejectedWithoutWritingFiles() throws IOException {
        stubDownloads("<html>Access Denied</html>", NseSymbolFixtures.ETF_CSV);

        assertThrows(ResponseStatusException.class, service::refreshSymbolLists);

        assertFalse(service.hasStoredSymbolLists());
    }

    private void stubDownloads(String equities, String etfs) throws IOException {
        when(client.fetchSymbolList(NseSymbolType.EQUITY)).thenReturn(equities);
        when(client.fetchSymbolList(NseSymbolType.ETF)).thenReturn(etfs);
    }
}
