package com.stocknews.dto;

import com.stocknews.model.Sector;
import com.stocknews.model.SectorMarket;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** markets are the countries the sector is offered for. */
public record SectorResponse(Integer id, String sectorName, List<SectorMarket> markets, LocalDateTime createdAt) {

    public static SectorResponse from(Sector sector) {
        List<SectorMarket> markets = Arrays.stream(SectorMarket.values()).filter(sector::isOfferedFor).toList();
        return new SectorResponse(sector.getId(), sector.getSectorName(), markets, sector.getCreatedAt());
    }
}
