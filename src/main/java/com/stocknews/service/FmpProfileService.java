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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serves FMP company profiles from PostgreSQL, calling FMP only for symbols whose saved row was not
 * updated today (in {@code fmp.profile-refresh-time-zone}) or that are not saved yet.
 */
@Service
@Slf4j
public class FmpProfileService {
    /** Each fetched symbol costs two FMP calls: profile + ratios-ttm (P/E, EPS). */
    static final int CALLS_PER_SYMBOL = 2;

    private final FmpProfileClient client;
    private final FmpStockProfileRepository profileRepository;
    private final FmpApiCallRepository apiCallRepository;
    private final FmpProperties properties;
    private final Clock clock;

    @Autowired
    public FmpProfileService(FmpProfileClient client, FmpStockProfileRepository profileRepository,
                             FmpApiCallRepository apiCallRepository, FmpProperties properties) {
        this(client, profileRepository, apiCallRepository, properties,
                Clock.system(ZoneId.of(properties.getProfileRefreshTimeZone())));
    }

    /** Uses {@code clock} to decide what "today" is and to stamp created/updated dates. */
    public FmpProfileService(FmpProfileClient client, FmpStockProfileRepository profileRepository,
                             FmpApiCallRepository apiCallRepository, FmpProperties properties, Clock clock) {
        this.client = client;
        this.profileRepository = profileRepository;
        this.apiCallRepository = apiCallRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Returns one profile per requested symbol, in request order. Profiles updated today come from
     * PostgreSQL; every other symbol is fetched from FMP, upserted, and returned.
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
            List<String> skipped = new ArrayList<>();
            for (String symbol : symbolsToFetch) {
                FmpStockProfile stored = profilesBySymbol.remove(symbol);
                try {
                    profilesBySymbol.put(symbol, fetchAndUpsert(symbol, stored));
                } catch (ResponseStatusException exception) {
                    // One bad symbol must not fail the whole request: log it and continue with the rest.
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
        // Symbols that could not be fetched are left out of the response.
        return symbols.stream().map(profilesBySymbol::get).filter(Objects::nonNull).toList();
    }

    private boolean isUpdatedOn(FmpStockProfile profile, LocalDate day) {
        return profile != null && profile.getUpdatedDate() != null
                && profile.getUpdatedDate().toLocalDate().isEqual(day);
    }

    private void validateCanFetch(List<String> symbolsToFetch) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "FMP API key is not configured; set FMP_API_KEY");
        }
        if (properties.isUsExchangesOnly()
                && symbolsToFetch.stream().anyMatch(symbol -> symbol.endsWith(".NS") || symbol.endsWith(".BO"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "FMP Basic profiles support US-exchange symbols only");
        }
        int dailyRequestLimit = properties.getDailyRequestLimit();
        if (dailyRequestLimit < 1) {
            throw new IllegalStateException("FMP daily request limit must be greater than zero");
        }
        checkDailyQuota(symbolsToFetch.size() * CALLS_PER_SYMBOL, dailyRequestLimit);
    }

    /**
     * Calls FMP for one symbol and saves it immediately, so a later symbol's failure cannot discard
     * profiles that were already fetched (and already counted against the quota).
     */
    private FmpStockProfile fetchAndUpsert(String symbol, FmpStockProfile stored) {
        recordApiCall();
        FmpStockProfile fetched = client.fetchProfile(symbol);
        recordApiCall();
        client.applyTtmRatios(fetched);

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

    private void checkDailyQuota(int requestedCalls, int dailyRequestLimit) {
        LocalDateTime now = LocalDateTime.now();
        long callsInLast24Hours;
        try {
            callsInLast24Hours = apiCallRepository.countByCalledAtAfter(now.minusHours(24));
        } catch (DataAccessException exception) {
            log.error("Failed to read FMP API usage from PostgreSQL:\n{}",
                    ExceptionLog.stackTrace(exception));
            throw exception;
        }
        if (callsInLast24Hours + requestedCalls > dailyRequestLimit) {
            ResponseStatusException exception = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "FMP daily request limit reached; try again after the quota window resets");
            log.error("FMP daily request limit reached ({} of {} calls used):\n{}",
                    callsInLast24Hours, dailyRequestLimit, ExceptionLog.stackTrace(exception));
            throw exception;
        }
    }
}
