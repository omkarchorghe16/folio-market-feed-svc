package com.stocknews.service;

import com.stocknews.dto.SectorRequest;
import com.stocknews.dto.SectorResponse;
import com.stocknews.exception.DuplicateResourceException;
import com.stocknews.model.SectorMarket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SectorService.class)
class SectorServiceTest {

    @Autowired
    private SectorService sectorService;

    @Test
    void marketsHaveSeparateListsInAlphabeticalOrder() {
        sectorService.create(new SectorRequest("Semi Equipment", null));
        sectorService.create(new SectorRequest("Pharmaceuticals", SectorMarket.INDIA));
        sectorService.create(new SectorRequest(" Cement ", SectorMarket.INDIA));

        assertEquals(List.of("Semi Equipment"), namesOf(SectorMarket.US));
        assertEquals(List.of("Cement", "Pharmaceuticals"), namesOf(SectorMarket.INDIA));
        assertEquals(3, sectorService.findAll().size());
    }

    @Test
    void aNameOfTheOtherMarketIsThenOfferedForBoth() {
        SectorResponse us = sectorService.create(new SectorRequest("Insurance", SectorMarket.US));
        SectorResponse both = sectorService.create(new SectorRequest("insurance", SectorMarket.INDIA));

        assertEquals(us.id(), both.id());
        assertEquals(List.of(SectorMarket.US, SectorMarket.INDIA), both.markets());
        assertThrows(DuplicateResourceException.class,
                () -> sectorService.create(new SectorRequest("INSURANCE", SectorMarket.INDIA)));
    }

    private List<String> namesOf(SectorMarket market) {
        return sectorService.findByMarket(market).stream().map(SectorResponse::sectorName).toList();
    }
}
