package com.stocknews.config;

import com.stocknews.controller.SectorController;
import com.stocknews.service.SectorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SectorController.class)
class WebConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SectorService sectorService;

    @Test
    void allowsPreflightFromAngularDevServer() throws Exception {
        mockMvc.perform(options("/api/sectors")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void rejectsPreflightFromOtherOrigins() throws Exception {
        mockMvc.perform(options("/api/sectors")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
