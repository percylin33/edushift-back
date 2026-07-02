package com.edushift.modules.academic.competency.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.academic.competency.dto.CapacityReorderRequest;
import com.edushift.modules.academic.competency.dto.CapacityResponse;
import com.edushift.modules.academic.competency.dto.CapacityResponse.CompetencyRef;
import com.edushift.modules.academic.competency.dto.CapacityResponse.CourseRef;
import com.edushift.modules.academic.competency.dto.CreateCapacityRequest;
import com.edushift.modules.academic.competency.dto.UpdateCapacityRequest;
import com.edushift.modules.academic.competency.service.CapacityService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = CapacityController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
    GlobalExceptionHandler.class,
    com.edushift.config.SecurityConfig.class,
    com.edushift.config.WebConfiguration.class
})
class CapacityControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CapacityService service;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

    private static final String COMP_BASE = "/academic/competencies/{competencyUuid}/capacities";
    private static final String FLAT_BASE = "/academic/capacities";

    private static JwtAuthenticationToken adminAuth() {
        var principal = new JwtAuthenticatedPrincipal(
            UUID.randomUUID(), UUID.randomUUID(), "acme", "admin@acme.test");
        return new JwtAuthenticationToken(principal, "fake.token",
            List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
    }

    private static JwtAuthenticationToken plainAuth() {
        var principal = new JwtAuthenticatedPrincipal(
            UUID.randomUUID(), UUID.randomUUID(), "acme", "user@acme.test");
        return new JwtAuthenticationToken(principal, "fake.token", List.<GrantedAuthority>of());
    }

    private CapacityResponse stubResponse() {
        return new CapacityResponse(UUID.randomUUID(),
            new CompetencyRef(UUID.randomUUID(), "MAT_C1", "Resuelve",
                new CourseRef(UUID.randomUUID(), "MAT", "Mat")),
            "MAT_C1_CAP1", "Traduce", null, 1, true,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("GET /academic/competencies/{c}/capacities")
    class ListCapacities {

        @Test
        @DisplayName("200 with array")
        void happyPath() throws Exception {
            given(service.listCapacities(any(), any())).willReturn(List.of(stubResponse()));

            mockMvc.perform(get(COMP_BASE, UUID.randomUUID()).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MAT_C1_CAP1"));
        }

        @Test
        @DisplayName("anonymous — 401")
        void anonymous() throws Exception {
            mockMvc.perform(get(COMP_BASE, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("plain auth — 403")
        void forbidden() throws Exception {
            mockMvc.perform(get(COMP_BASE, UUID.randomUUID()).with(authentication(plainAuth())))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /academic/competencies/{c}/capacities")
    class CreateCapacity {

        @Test
        @DisplayName("201 with ApiResponse")
        void happyPath() throws Exception {
            given(service.createCapacity(any(), any())).willReturn(stubResponse());

            mockMvc.perform(post(COMP_BASE, UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateCapacityRequest("MAT_C1_CAP1", "Traduce", null, 1, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("MAT_C1_CAP1"));
        }

        @Test
        @DisplayName("code taken — 409")
        void codeTaken() throws Exception {
            given(service.createCapacity(any(), any()))
                .willThrow(new ConflictException("CAPACITY_CODE_TAKEN", "taken"));

            mockMvc.perform(post(COMP_BASE, UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateCapacityRequest("MAT_C1_CAP1", "Traduce", null, 1, true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("CAPACITY_CODE_TAKEN"));
        }
    }

    @Nested
    @DisplayName("PATCH /academic/competencies/{c}/capacities/reorder")
    class ReorderCapacities {

        @Test
        @DisplayName("200 with ApiResponse array")
        void happyPath() throws Exception {
            given(service.reorderCapacities(any(), any())).willReturn(List.of(stubResponse()));

            var req = new CapacityReorderRequest(List.of(
                new CapacityReorderRequest.Item(UUID.randomUUID(), 1)));

            mockMvc.perform(patch(COMP_BASE + "/reorder", UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("MAT_C1_CAP1"));
        }
    }

    @Nested
    @DisplayName("GET /academic/capacities/{publicUuid}")
    class GetOne {

        @Test
        @DisplayName("200 with ApiResponse")
        void happyPath() throws Exception {
            var id = UUID.randomUUID();
            given(service.getCapacity(id)).willReturn(stubResponse());

            mockMvc.perform(get(FLAT_BASE + "/{id}", id).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("MAT_C1_CAP1"));
        }
    }

    @Nested
    @DisplayName("PUT /academic/capacities/{publicUuid}")
    class Update {

        @Test
        @DisplayName("200 with ApiResponse")
        void happyPath() throws Exception {
            var id = UUID.randomUUID();
            given(service.updateCapacity(eq(id), any())).willReturn(stubResponse());

            mockMvc.perform(put(FLAT_BASE + "/{id}", id)
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdateCapacityRequest(null, "Updated", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("MAT_C1_CAP1"));
        }
    }

    @Nested
    @DisplayName("DELETE /academic/capacities/{publicUuid}")
    class Delete {

        @Test
        @DisplayName("204")
        void happyPath() throws Exception {
            mockMvc.perform(delete(FLAT_BASE + "/{id}", UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth())))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("unknown — 404")
        void notFound() throws Exception {
            var id = UUID.randomUUID();
            org.mockito.BDDMockito.willThrow(new ResourceNotFoundException("Capacity", id))
                .given(service).deleteCapacity(id);

            mockMvc.perform(delete(FLAT_BASE + "/{id}", id)
                    .with(csrf()).with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
        }
    }
}
