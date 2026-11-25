package com.stocknews.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sectors")
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sector_id")
    private Integer id;

    @Column(name = "sector_name", nullable = false, unique = true, length = 100)
    private String sectorName;

    /** The database default lets Hibernate add the column to existing rows, which are all US sectors. */
    @Column(name = "is_us", nullable = false, columnDefinition = "boolean default true")
    private boolean us;

    @Column(name = "is_india", nullable = false, columnDefinition = "boolean default false")
    private boolean india;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Sector() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSectorName() {
        return sectorName;
    }

    public void setSectorName(String sectorName) {
        this.sectorName = sectorName;
    }

    public boolean isOfferedFor(SectorMarket market) {
        return market == SectorMarket.INDIA ? india : us;
    }

    public void offerFor(SectorMarket market) {
        if (market == SectorMarket.INDIA) {
            india = true;
        } else {
            us = true;
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
