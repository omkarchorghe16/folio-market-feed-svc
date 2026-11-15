package com.stocknews.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
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

/**
 * One Tapetide dataset (profile, financials, shareholding, forecasts, history) for a symbol and set of
 * request options, stored as Tapetide's JSON. Upserted on refresh.
 */
@Entity
@Table(name = "tapetide_stock_datasets",
        uniqueConstraints = @UniqueConstraint(name = "uk_tapetide_dataset",
                columnNames = {"symbol", "dataset", "request_params"}))
@Getter
@Setter
@NoArgsConstructor
public class TapetideStockDataset extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    /** profile, financials, shareholding, forecasts or history. */
    @Column(nullable = false, length = 30)
    private String dataset;

    /** Options used for the request, e.g. "section=ratios;periods=5" (empty when none). */
    @Column(name = "request_params", nullable = false, length = 200)
    private String requestParams;

    /** Tapetide's JSON response, returned as JSON (not a string) by the API. */
    @JsonRawValue
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;
}
