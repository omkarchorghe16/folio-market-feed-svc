package com.stocknews.repository;

import com.stocknews.model.UpstoxStockCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface UpstoxStockCandleRepository extends JpaRepository<UpstoxStockCandle, Long> {
    List<UpstoxStockCandle> findAllBySymbolAndExchangeAndUnitAndIntervalAndCandleTimeBetweenOrderByCandleTimeDesc(
            String symbol, String exchange, String unit, Integer interval, OffsetDateTime from, OffsetDateTime to);
}
