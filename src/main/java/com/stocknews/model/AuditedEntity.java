package com.stocknews.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Adds created/updated timestamps. Services set {@code updatedDate} on every refresh (even when the
 * data is unchanged); the JPA callbacks are fallbacks.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditedEntity {
    /** When this row was first saved; never changed by later refreshes. */
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    /** When this row was last refreshed from the provider. */
    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate;

    /** Marks the row as refreshed now; keeps the original created date. */
    public void touch(LocalDateTime now) {
        if (createdDate == null) {
            createdDate = now;
        }
        updatedDate = now;
    }

    @PrePersist
    void fillTimestampsOnCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdDate == null) {
            createdDate = now;
        }
        if (updatedDate == null) {
            updatedDate = createdDate;
        }
    }

    @PreUpdate
    void fillTimestampOnUpdate() {
        if (updatedDate == null) {
            updatedDate = LocalDateTime.now();
        }
    }
}
