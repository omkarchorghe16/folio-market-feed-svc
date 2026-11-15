package com.stocknews.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Latest Tapetide quote per NSE symbol (one row per symbol, upserted on refresh). */
@Entity
@Table(name = "tapetide_stock_quotes")
@Getter
@Setter
@NoArgsConstructor
public class TapetideStockQuote extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String symbol;

    private String name;

    @Column(precision = 24, scale = 8)
    private BigDecimal price;

    @Column(name = "price_change", precision = 24, scale = 8)
    private BigDecimal change;

    @Column(precision = 24, scale = 8)
    private BigDecimal changePct;

    @Column(precision = 24, scale = 8)
    private BigDecimal open;

    @Column(precision = 24, scale = 8)
    private BigDecimal high;

    @Column(precision = 24, scale = 8)
    private BigDecimal low;

    @Column(precision = 24, scale = 8)
    private BigDecimal prevClose;

    private Long volume;

    /** Trailing-twelve-month P/E (only in single-symbol quotes; batch quotes keep the last saved value). */
    @Column(precision = 24, scale = 8)
    private BigDecimal peTtm;

    @Column(precision = 24, scale = 8)
    private BigDecimal pb;

    @Column(name = "high_52w", precision = 24, scale = 8)
    private BigDecimal high52w;

    @Column(name = "low_52w", precision = 24, scale = 8)
    private BigDecimal low52w;

    private String lastTradeDate;
    private String priceBasis;
    /** Tapetide's own timestamp for this price. */
    private String providerUpdatedAt;

    @JsonRawValue
    @Column(columnDefinition = "TEXT")
    private String rawPayload;
}
