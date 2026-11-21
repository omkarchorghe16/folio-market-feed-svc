package com.stocknews.alphavantage;

import com.stocknews.model.AlphaVantageOverview;
import com.stocknews.repository.AlphaVantageOverviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlphaVantageOverviewService {
    private final AlphaVantageOverviewClient alphaVantageOverviewClient;
    private final AlphaVantageOverviewRepository alphaVantageOverviewRepository;

    @Transactional
    public List<AlphaVantageOverview> refreshOverviews(List<String> requestedSymbols) {
        List<String> symbols = requestedSymbols.stream()
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        List<AlphaVantageOverview> overviews = alphaVantageOverviewClient.fetchOverviews(symbols);
        Map<String, AlphaVantageOverview> existing = new LinkedHashMap<>();
        alphaVantageOverviewRepository.findAllBySymbolIn(symbols)
                .forEach(overview -> existing.put(overview.getSymbol(), overview));
        for (AlphaVantageOverview overview : overviews) {
            AlphaVantageOverview stored = existing.get(overview.getSymbol());
            if (stored != null) {
                overview.setId(stored.getId());
            }
        }
        List<AlphaVantageOverview> saved = alphaVantageOverviewRepository.saveAllAndFlush(overviews);
        log.info("Saved {} Alpha Vantage overviews to PostgreSQL", saved.size());
        return saved;
    }
}
