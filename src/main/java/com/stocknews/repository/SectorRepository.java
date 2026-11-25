package com.stocknews.repository;

import com.stocknews.model.Sector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectorRepository extends JpaRepository<Sector, Integer> {

    List<Sector> findByUsTrueOrderBySectorNameAsc();

    List<Sector> findByIndiaTrueOrderBySectorNameAsc();

    Optional<Sector> findBySectorNameIgnoreCase(String sectorName);
}
