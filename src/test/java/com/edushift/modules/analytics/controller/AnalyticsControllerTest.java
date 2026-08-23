package com.edushift.modules.analytics.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.analytics.service.AnalyticsService;
import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class AnalyticsControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AnalyticsService analyticsService;

    @Test
    void kpis_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/analytics/kpis"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void kpis_returnsOk_whenTenantAdmin() throws Exception {
        given(analyticsService.currentSummary()).willReturn(
                new com.edushift.modules.analytics.dto.KpiSummaryResponse(java.util.List.of()));

        mockMvc.perform(get("/v1/analytics/kpis").with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void kpis_returnsOk_whenTeacher() throws Exception {
        given(analyticsService.currentSummary()).willReturn(
                new com.edushift.modules.analytics.dto.KpiSummaryResponse(java.util.List.of()));

        mockMvc.perform(get("/v1/analytics/kpis").with(auth(teacher())))
                .andExpect(status().isOk());
    }
}
