package com.stocknews.us.nasdaq;

import com.stocknews.config.NasdaqProperties;
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

class NasdaqSymbolServiceTest {
    @TempDir
    Path tempDirectory;

    private Path storageDirectory;
    private NasdaqSymbolListClient nasdaqSymbolListClient;
    private NasdaqSymbolService nasdaqSymbolService;

    @BeforeEach
    void setUp() {
        storageDirectory = tempDirectory.resolve("us");
        nasdaqSymbolListClient = mock(NasdaqSymbolListClient.class);
        NasdaqProperties nasdaqProperties = new NasdaqProperties();
        nasdaqProperties.setStorageDirectory(storageDirectory.toString());
        nasdaqSymbolService = new NasdaqSymbolService(nasdaqSymbolListClient, nasdaqProperties);
    }

    @Test
    void refreshStoresBothFilesAndReportsCounts() throws IOException {
        stubDownloads(NasdaqSymbolFixtures.NASDAQ_LISTED, NasdaqSymbolFixtures.OTHER_LISTED);

        NasdaqSymbolService.SyncResult result = nasdaqSymbolService.refreshSymbolLists();

        assertEquals(5, result.equities());
        assertEquals(2, result.etfs());
        assertTrue(nasdaqSymbolService.hasStoredSymbolLists());
        assertEquals(NasdaqSymbolFixtures.OTHER_LISTED,
                Files.readString(storageDirectory.resolve(NasdaqSymbolFile.OTHER_LISTED.storedFileName())));
    }

    @Test
    void searchRanksExactAndPrefixMatchesFirstAcrossBothFiles() throws IOException {
        stubDownloads(NasdaqSymbolFixtures.NASDAQ_LISTED, NasdaqSymbolFixtures.OTHER_LISTED);
        nasdaqSymbolService.refreshSymbolLists();

        assertEquals("AAPL", nasdaqSymbolService.search("aapl", null, null).get(0).symbol());
        assertEquals(List.of("AAPL", "APLD"), tickers(nasdaqSymbolService.search("appl", null, null)));
        assertEquals("F", nasdaqSymbolService.search("f", null, null).get(0).symbol());
        assertEquals(List.of("BRK.B"), tickers(nasdaqSymbolService.search("berkshire", null, null)));
    }

    @Test
    void searchFiltersByTypeAndAppliesLimit() throws IOException {
        stubDownloads(NasdaqSymbolFixtures.NASDAQ_LISTED, NasdaqSymbolFixtures.OTHER_LISTED);
        nasdaqSymbolService.refreshSymbolLists();

        assertEquals(List.of("QQQ", "SPY"), tickers(nasdaqSymbolService.search(null, NasdaqSymbolType.ETF, null)));
        assertEquals(7, nasdaqSymbolService.search("", null, null).size());
        assertEquals(1, nasdaqSymbolService.search("common stock", NasdaqSymbolType.EQUITY, 1).size());
        assertTrue(nasdaqSymbolService.search("trust", NasdaqSymbolType.EQUITY, null).isEmpty());
    }

    @Test
    void searchBeforeFirstSyncIsServiceUnavailable() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> nasdaqSymbolService.search("aapl", null, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void failedDownloadKeepsPreviousFiles() throws IOException {
        stubDownloads(NasdaqSymbolFixtures.NASDAQ_LISTED, NasdaqSymbolFixtures.OTHER_LISTED);
        nasdaqSymbolService.refreshSymbolLists();
        when(nasdaqSymbolListClient.fetchSymbolList(NasdaqSymbolFile.OTHER_LISTED))
                .thenThrow(new IOException("HTTP 503"));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, nasdaqSymbolService::refreshSymbolLists);

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals(7, nasdaqSymbolService.search(null, null, null).size());
    }

    @Test
    void invalidDownloadIsRejectedWithoutWritingFiles() throws IOException {
        stubDownloads(NasdaqSymbolFixtures.NASDAQ_LISTED, "<html>Service Unavailable</html>");

        assertThrows(ResponseStatusException.class, nasdaqSymbolService::refreshSymbolLists);

        assertFalse(nasdaqSymbolService.hasStoredSymbolLists());
        assertFalse(Files.exists(storageDirectory.resolve(NasdaqSymbolFile.NASDAQ_LISTED.storedFileName())));
    }

    private void stubDownloads(String nasdaqListed, String otherListed) throws IOException {
        when(nasdaqSymbolListClient.fetchSymbolList(NasdaqSymbolFile.NASDAQ_LISTED)).thenReturn(nasdaqListed);
        when(nasdaqSymbolListClient.fetchSymbolList(NasdaqSymbolFile.OTHER_LISTED)).thenReturn(otherListed);
    }

    private static List<String> tickers(List<NasdaqSymbol> symbols) {
        return symbols.stream().map(NasdaqSymbol::symbol).toList();
    }
}
