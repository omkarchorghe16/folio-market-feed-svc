package com.stocknews.service;

import com.stocknews.dto.SectorRequest;
import com.stocknews.dto.SectorResponse;
import com.stocknews.exception.DuplicateResourceException;
import com.stocknews.exception.ResourceNotFoundException;
import com.stocknews.model.Sector;
import com.stocknews.model.SectorMarket;
import com.stocknews.repository.SectorRepository;
import com.stocknews.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SectorService {

    private final SectorRepository sectorRepository;
    private final StockRepository stockRepository;

    public SectorService(SectorRepository sectorRepository, StockRepository stockRepository) {
        this.sectorRepository = sectorRepository;
        this.stockRepository = stockRepository;
    }

    /** A name that exists for the other market only is from now on offered for both. */
    @Transactional
    public SectorResponse create(SectorRequest request) {
        String name = request.sectorName().trim();
        SectorMarket market = request.market() == null ? SectorMarket.US : request.market();
        Sector sector = sectorRepository.findBySectorNameIgnoreCase(name).orElseGet(() -> newSector(name));
        if (sector.isOfferedFor(market)) {
            throw new DuplicateResourceException("Sector already exists: " + name);
        }
        sector.offerFor(market);
        return SectorResponse.from(sectorRepository.save(sector));
    }

    @Transactional(readOnly = true)
    public List<SectorResponse> findAll() {
        return sectorRepository.findAll().stream().map(SectorResponse::from).toList();
    }

    /** A to Z. */
    @Transactional(readOnly = true)
    public List<SectorResponse> findByMarket(SectorMarket market) {
        List<Sector> sectors = market == SectorMarket.INDIA
                ? sectorRepository.findByIndiaTrueOrderBySectorNameAsc()
                : sectorRepository.findByUsTrueOrderBySectorNameAsc();
        return sectors.stream().map(SectorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SectorResponse findById(Integer id) {
        return SectorResponse.from(getOrThrow(id));
    }

    @Transactional
    public SectorResponse update(Integer id, SectorRequest request) {
        Sector sector = getOrThrow(id);
        String name = request.sectorName().trim();
        sectorRepository.findBySectorNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Sector already exists: " + name);
                });
        sector.setSectorName(name);
        return SectorResponse.from(sectorRepository.save(sector));
    }

    @Transactional
    public void delete(Integer id) {
        Sector sector = getOrThrow(id);
        if (stockRepository.existsBySectorId(id)) {
            throw new DuplicateResourceException(
                    "Sector '" + sector.getSectorName() + "' still has stocks. Delete or move them first.");
        }
        sectorRepository.delete(sector);
    }

    private static Sector newSector(String name) {
        Sector sector = new Sector();
        sector.setSectorName(name);
        return sector;
    }

    private Sector getOrThrow(Integer id) {
        return sectorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sector not found: " + id));
    }
}
