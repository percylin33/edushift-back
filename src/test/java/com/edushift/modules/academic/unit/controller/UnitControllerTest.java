package com.edushift.modules.academic.unit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.academic.unit.service.UnitService;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UnitController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class UnitControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UnitService service;

    @Test
    void list_returnsOk_whenTenantAdmin() throws Exception {
        UUID courseUuid = UUID.randomUUID();
        given(service.listUnits(any(), any())).willReturn(List.of());

        mockMvc.perform(get("/v1/academic/courses/{c}/units", courseUuid).with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void list_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/academic/courses/{c}/units", UUID.randomUUID()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void list_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/academic/courses/{c}/units", UUID.randomUUID()).with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
