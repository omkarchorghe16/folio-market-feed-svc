package com.stocknews.dto;

import com.stocknews.model.SectorMarket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** market is used when creating a sector; without one the sector is for the US. */
public record SectorRequest(
        @NotBlank(message = "sectorName is required")
        @Size(max = 100, message = "sectorName must be at most 100 characters")
        String sectorName,
        SectorMarket market) {
}
