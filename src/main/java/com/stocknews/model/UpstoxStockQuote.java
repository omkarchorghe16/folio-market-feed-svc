package com.stocknews.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Latest Upstox market quote per symbol and exchange (upserted on every fetch). */
@Entity
@Table(name = "upstox_stock_quotes",
        uniqueConstraints = @UniqueConstraint(name = "uk_upstox_quote", columnNames = {"symbol", "exchange"}))
@Getter
@Setter
@NoArgsConstructor
public class UpstoxStockQuote extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String exchange;

    private String name;
    private String isin;
    private String instrumentKey;

    @Column(precision = 24, scale = 8)
    private BigDecimal lastPrice;

    @Column(precision = 24, scale = 8)
    private BigDecimal open;

    @Column(precision = 24, scale = 8)
    private BigDecimal high;

    @Column(precision = 24, scale = 8)
    private BigDecimal low;

    @Column(name = "close_price", precision = 24, scale = 8)
    private BigDecimal close;

    @Column(precision = 24, scale = 8)
    private BigDecimal netChange;

    private Long volume;

    @Column(precision = 24, scale = 8)
    private BigDecimal averagePrice;

    @Column(precision = 24, scale = 8)
    private BigDecimal lowerCircuitLimit;

    @Column(precision = 24, scale = 8)
    private BigDecimal upperCircuitLimit;

    /** Upstox's timestamp for this quote. */
    private String quoteTimestamp;
}
