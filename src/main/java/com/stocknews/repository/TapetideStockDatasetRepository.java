package com.stocknews.repository;

import com.stocknews.model.TapetideStockDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TapetideStockDatasetRepository extends JpaRepository<TapetideStockDataset, Long> {
    Optional<TapetideStockDataset> findBySymbolAndDatasetAndRequestParams(String symbol, String dataset,
                                                                           String requestParams);
}
