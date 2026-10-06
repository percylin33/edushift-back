package com.edushift.modules.academic.unit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.academic.unit.dto.SeedUnitsResponse;
import com.edushift.modules.academic.unit.service.UnitSeedService;
import com.edushift.modules.academic.unit.service.UnitService;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UnitController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class UnitControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UnitService service;
    @MockitoBean UnitSeedService seedService;

    @Test
    void list_returnsOk_whenTenantAdmin() throws Exception {
        UUID courseUuid = UUID.randomUUID();
        given(service.listUnits(any(), any())).willReturn(List.of());

        mockMvc.perform(get("/v1/academic/courses/{c}/units", courseUuid).with(auth(tenantAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void list_returnsOk_whenTeacher() throws Exception {
        UUID courseUuid = UUID.randomUUID();
        given(service.listUnits(any(), any())).willReturn(List.of());

        mockMvc.perform(get("/v1/academic/courses/{c}/units", courseUuid).with(auth(teacher())))
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

    @Test
    @DisplayName("POST seed-defaults — TENANT_ADMIN → 200")
    void seedDefaults_returnsOk_whenTenantAdmin() throws Exception {
        UUID courseUuid = UUID.randomUUID();
        given(seedService.seedForCourse(eq(courseUuid), eq(8), eq(true)))
                .willReturn(new SeedUnitsResponse(true, 8, 8, List.of()));

        mockMvc.perform(post("/v1/academic/courses/{c}/units/seed-defaults", courseUuid)
                        .with(csrf())
                        .with(auth(tenantAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":8,\"saveAsTenantDefault\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seeded").value(true))
                .andExpect(jsonPath("$.data.countRequested").value(8))
                .andExpect(jsonPath("$.data.unitsCreated").value(8));

        verify(seedService).seedForCourse(courseUuid, 8, true);
    }

    @Test
    @DisplayName("POST seed-defaults — empty body uses tenant default")
    void seedDefaults_emptyBody() throws Exception {
        UUID courseUuid = UUID.randomUUID();
        given(seedService.seedForCourse(eq(courseUuid), isNull(), eq(false)))
                .willReturn(new SeedUnitsResponse(false, 8, 0, List.of()));

        mockMvc.perform(post("/v1/academic/courses/{c}/units/seed-defaults", courseUuid)
                        .with(csrf())
                        .with(auth(tenantAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seeded").value(false));

        verify(seedService).seedForCourse(courseUuid, null, false);
    }

    @Test
    @DisplayName("POST seed-defaults — TEACHER → 403")
    void seedDefaults_forbiddenForTeacher() throws Exception {
        mockMvc.perform(post("/v1/academic/courses/{c}/units/seed-defaults", UUID.randomUUID())
                        .with(csrf())
                        .with(auth(teacher())))
                .andExpect(status().isForbidden());
    }
}
