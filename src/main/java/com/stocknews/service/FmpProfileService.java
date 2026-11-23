package com.stocknews.service;

import com.stocknews.client.FmpProfileClient;
import com.stocknews.config.FmpProperties;
import com.stocknews.logging.ExceptionLog;
import com.stocknews.model.FmpApiCall;
import com.stocknews.model.FmpStockProfile;
import com.stocknews.repository.FmpApiCallRepository;
import com.stocknews.repository.FmpStockProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serves FMP company profiles from PostgreSQL, calling FMP only for symbols whose saved row was not
 * updated today (in {@code fmp.profile-refresh-time-zone}) or that are not saved yet, and only as many as the
 * daily request limit allows; the rest are served from their saved rows.
 */
@Service
@Slf4j
public class FmpProfileService {
    /** Each fetched symbol costs two FMP calls: profile + ratios-ttm (P/E, EPS). */
    static final int CALLS_PER_SYMBOL = 2;

    private final FmpProfileClient fmpProfileClient;
    private final FmpStockProfileRepository profileRepository;
    private final FmpApiCallRepository apiCallRepository;
    private final FmpProperties fmpProperties;
    private final Clock clock;

    @Autowired
    public FmpProfileService(FmpProfileClient fmpProfileClient, FmpStockProfileRepository profileRepository,
                             FmpApiCallRepository apiCallRepository, FmpProperties fmpProperties) {
        this(fmpProfileClient, profileRepository, apiCallRepository, fmpProperties,
                Clock.system(ZoneId.of(fmpProperties.getProfileRefreshTimeZone())));
    }

    /** Uses {@code clock} to decide what "today" is and to stamp created/updated dates. */
    public FmpProfileService(FmpProfileClient fmpProfileClient, FmpStockProfileRepository profileRepository,
                             FmpApiCallRepository apiCallRepository, FmpProperties fmpProperties, Clock clock) {
        this.fmpProfileClient = fmpProfileClient;
        this.profileRepository = profileRepository;
        this.apiCallRepository = apiCallRepository;
        this.fmpProperties = fmpProperties;
        this.clock = clock;
    }

    /**
     * Returns one profile per requested symbol, in request order. Profiles updated today come from
     * PostgreSQL; other symbols are fetched from FMP, upserted, and returned, as far as the daily quota allows.
     * When the quota (or an FMP failure) leaves a symbol unrefreshed, its saved row is returned as it is; a symbol
     * with no saved row is left out. Fails with 429 only when the quota is used up and nothing can be returned.
     */
    public synchronized List<FmpStockProfile> getProfiles(List<String> requestedSymbols) {
        List<String> symbols = StockSymbolNormalizer.normalize(requestedSymbols);

        Map<String, FmpStockProfile> profilesBySymbol = new LinkedHashMap<>();
        try {
            profileRepository.findAllBySymbolIn(symbols)
                    .forEach(profile -> profilesBySymbol.put(profile.getSymbol(), profile));
        } catch (DataAccessException exception) {
            log.error("Failed to read FMP profiles from PostgreSQL:\n{}",
                    ExceptionLog.stackTrace(exception));
            throw exception;
        }

        LocalDate today = LocalDate.now(clock);
        List<String> symbolsToFetch = symbols.stream()
                .filter(symbol -> !isUpdatedOn(profilesBySymbol.get(symbol), today))
                .toList();
        log.info("FMP profiles: {} requested, {} served from PostgreSQL, {} to fetch from FMP",
                symbols.size(), symbols.size() - symbolsToFetch.size(), symbolsToFetch.size());

        if (!symbolsToFetch.isEmpty()) {
            validateCanFetch(symbolsToFetch);
            int affordableSymbols = affordableSymbolCount();
            List<String> symbolsWithinQuota = fetchOrder(symbolsToFetch, profilesBySymbol).stream()
                    .limit(affordableSymbols)
                    .toList();
            if (symbolsWithinQuota.size() < symbolsToFetch.size()) {
                log.warn("FMP daily request limit leaves room for {} of {} symbols; "
                                + "returning saved profiles for the other {}",
                        symbolsWithinQuota.size(), symbolsToFetch.size(),
                        symbolsToFetch.size() - symbolsWithinQuota.size());
            }
            fetchAndUpsertAll(symbolsWithinQuota, profilesBySymbol);
            if (symbolsWithinQuota.isEmpty() && profilesBySymbol.isEmpty()) {
                log.warn("FMP daily request limit reached and none of the {} requested symbols is saved",
                        symbols.size());
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "FMP daily request limit reached; try again after the quota window resets");
            }
        }
        // Symbols that could not be fetched and have no saved row are left out of the response.
        return symbols.stream().map(profilesBySymbol::get).filter(Objects::nonNull).toList();
    }

    /**
     * Fetches each symbol in turn. A failed symbol keeps its saved row (if any) and does not stop the rest,
     * except an FMP rate limit, which stops further calls in this request.
     */
    private void fetchAndUpsertAll(List<String> symbolsToFetch, Map<String, FmpStockProfile> profilesBySymbol) {
        List<String> skipped = new ArrayList<>();
        for (String symbol : symbolsToFetch) {
            try {
                profilesBySymbol.put(symbol, fetchAndUpsert(symbol, profilesBySymbol.get(symbol)));
            } catch (ResponseStatusException exception) {
                skipped.add(symbol);
                log.error("Skipping FMP profile for {}: status={}, reason={}",
                        symbol, exception.getStatusCode().value(), exception.getReason());
                if (exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    log.error("FMP rate limit hit; not fetching remaining symbols this request");
                    break;
                }
            }
        }
        if (!skipped.isEmpty()) {
            log.warn("FMP profiles skipped for {} symbol(s): {}", skipped.size(), skipped);
        }
    }

    /**
     * Order in which stale symbols get the remaining quota: symbols with no saved row first (otherwise they
     * would show nothing), then the oldest saved rows.
     */
    private static List<String> fetchOrder(List<String> symbolsToFetch, Map<String, FmpStockProfile> saved) {
        Comparator<String> oldestFirst = Comparator.comparing(
                (String symbol) -> saved.get(symbol) == null ? null : saved.get(symbol).getUpdatedDate(),
                Comparator.nullsFirst(Comparator.naturalOrder()));
        return symbolsToFetch.stream().sorted(oldestFirst).toList();
    }

    private boolean isUpdatedOn(FmpStockProfile profile, LocalDate day) {
        return profile != null && profile.getUpdatedDate() != null
                && profile.getUpdatedDate().toLocalDate().isEqual(day);
    }

    private void validateCanFetch(List<String> symbolsToFetch) {
        if (fmpProperties.getApiKey() == null || fmpProperties.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "FMP API key is not configured; set FMP_API_KEY");
        }
        if (fmpProperties.isUsExchangesOnly()
                && symbolsToFetch.stream().anyMatch(symbol -> symbol.endsWith(".NS") || symbol.endsWith(".BO"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "FMP Basic profiles support US-exchange symbols only");
        }
    }

    /**
     * Calls FMP for one symbol and saves it immediately, so a later symbol's failure cannot discard
     * profiles that were already fetched (and already counted against the quota).
     */
    private FmpStockProfile fetchAndUpsert(String symbol, FmpStockProfile stored) {
        recordApiCall();
        FmpStockProfile fetched = fmpProfileClient.fetchProfile(symbol);
        recordApiCall();
        fmpProfileClient.applyTtmRatios(fetched);

        LocalDateTime now = LocalDateTime.now(clock);
        if (stored != null) {
            fetched.setId(stored.getId());
            fetched.setCreatedDate(stored.getCreatedDate() != null ? stored.getCreatedDate() : now);
        } else {
            fetched.setCreatedDate(now);
        }
        fetched.setUpdatedDate(now);

        try {
            FmpStockProfile saved = profileRepository.saveAndFlush(fetched);
            log.info("Saved FMP profile for {} to PostgreSQL ({})", symbol, stored == null ? "created" : "updated");
            return saved;
        } catch (DataAccessException exception) {
            log.error("Failed to persist FMP profile for {} to PostgreSQL:\n{}",
                    symbol, ExceptionLog.stackTrace(exception));
            throw exception;
        }
    }

    private void recordApiCall() {
        FmpApiCall call = new FmpApiCall();
        call.setCalledAt(LocalDateTime.now());
        try {
            apiCallRepository.saveAndFlush(call);
        } catch (DataAccessException exception) {
            log.error("Failed to record FMP API usage in PostgreSQL:\n{}",
                    ExceptionLog.stackTrace(exception));
            throw exception;
        }
    }

    /** How many symbols the calls left in the rolling 24-hour window can pay for. */
    private int affordableSymbolCount() {
        int dailyRequestLimit = fmpProperties.getDailyRequestLimit();
        if (dailyRequestLimit < 1) {
            throw new IllegalStateException("FMP daily request limit must be greater than zero");
        }
        long callsInLast24Hours;
        try {
            callsInLast24Hours = apiCallRepository.countByCalledAtAfter(LocalDateTime.now().minusHours(24));
        } catch (DataAccessException exception) {
            log.error("Failed to read FMP API usage from PostgreSQL:\n{}",
                    ExceptionLog.stackTrace(exception));
            throw exception;
        }
        long remainingCalls = Math.max(dailyRequestLimit - callsInLast24Hours, 0);
        if (remainingCalls < CALLS_PER_SYMBOL) {
            log.warn("FMP daily request limit reached ({} of {} calls used in the last 24 hours)",
                    callsInLast24Hours, dailyRequestLimit);
        }
        return (int) (remainingCalls / CALLS_PER_SYMBOL);
    }
}
