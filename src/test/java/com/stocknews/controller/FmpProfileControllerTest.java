package com.stocknews.controller;

import com.stocknews.model.FmpStockProfile;
import com.stocknews.service.FmpProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FmpProfileController.class)
class FmpProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FmpProfileService fmpProfileService;

    @Test
    void fetchProfilesAcceptsSymbolList() throws Exception {
        FmpStockProfile profile = new FmpStockProfile();
        profile.setSymbol("AAPL");
        profile.setCompanyName("Apple Inc.");
        profile.setCountry("US");
        profile.setCreatedDate(LocalDateTime.of(2026, 11, 1, 9, 0));
        profile.setUpdatedDate(LocalDateTime.of(2026, 11, 14, 10, 0));
        when(fmpProfileService.getProfiles(List.of("AAPL"))).thenReturn(List.of(profile));

        mockMvc.perform(post("/api/fmp/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"AAPL\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].country").value("US"))
                .andExpect(jsonPath("$[0].createdDate").value("2026-11-01T09:00:00"))
                .andExpect(jsonPath("$[0].updatedDate").value("2026-11-14T10:00:00"));

        verify(fmpProfileService).getProfiles(List.of("AAPL"));
    }

    @Test
    void fetchProfilesRejectsEmptySymbolList() throws Exception {
        mockMvc.perform(post("/api/fmp/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProfilesAcceptsCommaSeparatedSymbolsAndReturnsFullProfiles() throws Exception {
        FmpStockProfile aapl = new FmpStockProfile();
        aapl.setSymbol("AAPL");
        aapl.setCompanyName("Apple Inc.");
        aapl.setCountry("US");
        FmpStockProfile msft = new FmpStockProfile();
        msft.setSymbol("MSFT");
        msft.setCompanyName("Microsoft Corporation");
        msft.setCountry("US");
        when(fmpProfileService.getProfiles(List.of("AAPL", "MSFT")))
                .thenReturn(List.of(aapl, msft));

        mockMvc.perform(get("/api/fmp/profiles").param("symbols", "AAPL,MSFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].country").value("US"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[1].symbol").value("MSFT"));

        verify(fmpProfileService).getProfiles(List.of("AAPL", "MSFT"));
    }

    @Test
    void getProfilesRequiresSymbols() throws Exception {
        mockMvc.perform(get("/api/fmp/profiles"))
                .andExpect(status().isBadRequest());
    }
}
