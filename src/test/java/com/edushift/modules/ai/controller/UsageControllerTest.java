package com.edushift.modules.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.ai.repository.TenantAiSettingsRepository;
import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import com.edushift.modules.ai.service.AiTenantKeyResolver;
import com.edushift.test.AbstractControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsageController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class UsageControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TenantAiUsageRepository usageRepo;
    @MockitoBean TenantAiSettingsRepository settingsRepo;
    @MockitoBean AiTenantKeyResolver aiTenantKeyResolver;

    @Test
    void summary_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/ai/usage/summary"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void summary_returnsOk_whenTenantAdmin() throws Exception {
        given(aiTenantKeyResolver.resolve(any())).willReturn(ANY_TENANT);
        given(settingsRepo.findActiveByTenantId(any())).willReturn(java.util.Optional.empty());
        given(usageRepo.findDailyUsageThisMonth(any(), any())).willReturn(Page.empty());
        given(usageRepo.sumUsageThisMonthByFeature(any())).willReturn(java.util.List.of());

        mockMvc.perform(get("/v1/ai/usage/summary").with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
