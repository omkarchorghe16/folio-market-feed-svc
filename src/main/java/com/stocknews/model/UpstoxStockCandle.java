package com.stocknews.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One Upstox OHLCV candle; re-fetching the same candle updates it instead of adding a duplicate. */
@Entity
@Table(name = "upstox_stock_candles",
        uniqueConstraints = @UniqueConstraint(name = "uk_upstox_candle",
                columnNames = {"symbol", "exchange", "unit", "interval_size", "candle_time"}),
        indexes = @Index(name = "ix_upstox_candle_series", columnList = "symbol, exchange, unit, interval_size"))
@Getter
@Setter
@NoArgsConstructor
public class UpstoxStockCandle extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String exchange;

    /** minutes, hours, days, weeks or months. */
    @Column(nullable = false, length = 10)
    private String unit;

    /** Candle size in {@link #unit}s; "interval" is a reserved word in PostgreSQL. */
    @Column(name = "interval_size", nullable = false)
    private Integer interval;

    @Column(name = "candle_time", nullable = false)
    private OffsetDateTime candleTime;

    @Column(precision = 24, scale = 8)
    private BigDecimal open;

    @Column(precision = 24, scale = 8)
    private BigDecimal high;

    @Column(precision = 24, scale = 8)
    private BigDecimal low;

    @Column(name = "close_price", precision = 24, scale = 8)
    private BigDecimal close;

    private Long volume;
    private Long openInterest;
}
