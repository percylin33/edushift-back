package com.edushift.modules.tenants.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.tenants.service.PermissionOverrideService;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PermissionOverrideController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class PermissionOverrideControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PermissionOverrideService overrideService;

    @Test
    void list_returnsOk_whenTenantAdmin() throws Exception {
        given(overrideService.listAll(any())).willReturn(List.of());

        mockMvc.perform(get("/v1/tenants/me/permission-overrides").with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void list_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/tenants/me/permission-overrides"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void list_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/tenants/me/permission-overrides").with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
