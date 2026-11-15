package com.stocknews.client;

import com.stocknews.config.FmpProperties;
import com.stocknews.model.FmpApiCall;
import com.stocknews.model.FmpStockProfile;
import com.stocknews.repository.FmpApiCallRepository;
import com.stocknews.repository.FmpStockProfileRepository;
import com.stocknews.service.FmpProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FmpProfileServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Chicago");
    /** 2026-11-14 10:00 in Chicago. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private FmpProfileClient client;

    @Mock
    private FmpStockProfileRepository profileRepository;

    @Mock
    private FmpApiCallRepository apiCallRepository;

    private FmpProperties properties;
    private FmpProfileService service;

    @BeforeEach
    void setUp() {
        properties = new FmpProperties();
        properties.setApiKey("test-api-key");
        service = new FmpProfileService(client, profileRepository, apiCallRepository, properties, CLOCK);
    }

    @Test
    void fetchesNewSymbolOnceSetsBothDatesAndTracksQuota() {
        FmpStockProfile fetched = profile("AAPL", null);
        when(profileRepository.findAllBySymbolIn(List.of("AAPL"))).thenReturn(List.of());
        when(apiCallRepository.countByCalledAtAfter(any())).thenReturn(0L);
        when(client.fetchProfile("AAPL")).thenReturn(fetched);
        when(profileRepository.saveAndFlush(fetched)).thenReturn(fetched);

        List<FmpStockProfile> result = service.getProfiles(List.of(" aapl ", "AAPL"));

        assertEquals(List.of(fetched), result);
        assertEquals(NOW, fetched.getCreatedDate());
        assertEquals(NOW, fetched.getUpdatedDate());
        verify(apiCallRepository, times(2)).saveAndFlush(any(FmpApiCall.class));
        verify(client, times(1)).fetchProfile("AAPL");
        verify(client, times(1)).applyTtmRatios(fetched);
    }

    @Test
    void servesProfileUpdatedTodayFromDatabaseWithoutCallingFmp() {
        FmpStockProfile saved = profile("AAPL", NOW.toLocalDate().atStartOfDay().plusMinutes(1));
        when(profileRepository.findAllBySymbolIn(List.of("AAPL"))).thenReturn(List.of(saved));

        List<FmpStockProfile> result = service.getProfiles(List.of("AAPL"));

        assertEquals(List.of(saved), result);
        verify(client, never()).fetchProfile(any());
        verify(apiCallRepository, never()).countByCalledAtAfter(any());
        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void servesTodayProfileEvenWhenApiKeyIsMissing() {
        properties.setApiKey("");
        FmpStockProfile saved = profile("AAPL", NOW.minusHours(2));
        when(profileRepository.findAllBySymbolIn(List.of("AAPL"))).thenReturn(List.of(saved));

        assertEquals(List.of(saved), service.getProfiles(List.of("AAPL")));
    }

    @Test
    void refreshesProfileUpdatedYesterdayEvenIfLessThan24HoursAgo() {
        FmpStockProfile saved = profile("AAPL", NOW.toLocalDate().atStartOfDay().minusMinutes(1));
        saved.setId(42L);
        LocalDateTime originalCreated = LocalDateTime.of(2026, 1, 5, 9, 30);
        saved.setCreatedDate(originalCreated);
        FmpStockProfile fetched = profile("AAPL", null);
        fetched.setCompanyName("Updated Name");
        when(profileRepository.findAllBySymbolIn(List.of("AAPL"))).thenReturn(List.of(saved));
        when(apiCallRepository.countByCalledAtAfter(any())).thenReturn(0L);
        when(client.fetchProfile("AAPL")).thenReturn(fetched);
        when(profileRepository.saveAndFlush(fetched)).thenReturn(fetched);

        FmpStockProfile result = service.getProfiles(List.of("AAPL")).getFirst();

        assertEquals(42L, result.getId());
        assertEquals("Updated Name", result.getCompanyName());
        assertEquals(originalCreated, result.getCreatedDate());
        assertEquals(NOW, result.getUpdatedDate());
    }

    @Test
    void callsFmpOnlyForStaleSymbolsAndKeepsRequestOrder() {
        FmpStockProfile msftToday = profile("MSFT", NOW.minusHours(1));
        FmpStockProfile aaplOld = profile("AAPL", NOW.minusDays(3));
        FmpStockProfile aaplFresh = profile("AAPL", null);
        FmpStockProfile nvdaNew = profile("NVDA", null);
        when(profileRepository.findAllBySymbolIn(List.of("AAPL", "MSFT", "NVDA")))
                .thenReturn(List.of(msftToday, aaplOld));
        when(apiCallRepository.countByCalledAtAfter(any())).thenReturn(0L);
        when(client.fetchProfile("AAPL")).thenReturn(aaplFresh);
        when(client.fetchProfile("NVDA")).thenReturn(nvdaNew);
        when(profileRepository.saveAndFlush(any(FmpStockProfile.class))).thenAnswer(call -> call.getArgument(0));

        List<FmpStockProfile> result = service.getProfiles(List.of("AAPL", "MSFT", "NVDA"));

        assertSame(aaplFresh, result.get(0));
        assertSame(msftToday, result.get(1));
        assertSame(nvdaNew, result.get(2));
        verify(client, never()).fetchProfile("MSFT");
        verify(apiCallRepository, times(4)).saveAndFlush(any(FmpApiCall.class));
    }

    @Test
    void skipsFailedSymbolAndContinuesWithTheRest() {
        FmpStockProfile aapl = profile("AAPL", null);
        FmpStockProfile msft = profile("MSFT", null);
        when(profileRepository.findAllBySymbolIn(List.of("AAPL", "BAD", "MSFT"))).thenReturn(List.of());
        when(apiCallRepository.countByCalledAtAfter(any())).thenReturn(0L);
        when(client.fetchProfile("AAPL")).thenReturn(aapl);
        when(client.fetchProfile("BAD")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "FMP returned no profile for BAD"));
        when(client.fetchProfile("MSFT")).thenReturn(msft);
        when(profileRepository.saveAndFlush(any(FmpStockProfile.class))).thenAnswer(call -> call.getArgument(0));

        List<FmpStockProfile> result = service.getProfiles(List.of("AAPL", "BAD", "MSFT"));

        assertEquals(List.of(aapl, msft), result);
    }

    @Test
    void stopsFetchingAfterFmpRateLimitButReturnsWhatWasFetched() {
        FmpStockProfile aapl = profile("AAPL", null);
        when(profileRepository.findAllBySymbolIn(List.of("AAPL", "MSFT", "NVDA"))).thenReturn(List.of());
        when(apiCallRepository.countByCalledAtAfter(any())).thenReturn(0L);
        when(client.fetchProfile("AAPL")).thenReturn(aapl);
        when(client.fetchProfile("MSFT")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "FMP profile lookup failed for MSFT"));
        when(profileRepository.saveAndFlush(aapl)).thenReturn(aapl);

        List<FmpStockProfile> result = service.getProfiles(List.of("AAPL", "MSFT", "NVDA"));

        assertEquals(List.of(aapl), result);
        verify(client, never()).fetchProfile("NVDA");
    }

    @Test
    void rejectsRequestsThatWouldExceedTheConfiguredDailyQuota() {
        when(profileRepository.findAllBySymbolIn(List.of("AAPL", "MSFT"))).thenReturn(List.of());
        when(apiCallRepository.countByCalledAtAfter(any())).thenReturn(249L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getProfiles(List.of("AAPL", "MSFT")));

        assertEquals(429, exception.getStatusCode().value());
        verify(client, never()).fetchProfile(any());
    }

    @Test
    void countsTwoCallsPerSymbolAgainstDailyQuota() {
        when(profileRepository.findAllBySymbolIn(List.of("AAPL"))).thenReturn(List.of());
        when(apiCallRepository.countByCalledAtAfter(any())).thenReturn(249L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getProfiles(List.of("AAPL")));

        assertEquals(429, exception.getStatusCode().value());
        verify(client, never()).fetchProfile(any());
    }

    @Test
    void rejectsIndianSymbolsThatNeedFetchingOnTheBasicPlan() {
        when(profileRepository.findAllBySymbolIn(List.of("RELIANCE.NS"))).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getProfiles(List.of("RELIANCE.NS")));

        assertEquals(400, exception.getStatusCode().value());
        verify(apiCallRepository, never()).countByCalledAtAfter(any());
    }

    private static FmpStockProfile profile(String symbol, LocalDateTime updatedDate) {
        FmpStockProfile profile = new FmpStockProfile();
        profile.setSymbol(symbol);
        profile.setUpdatedDate(updatedDate);
        return profile;
    }
}
