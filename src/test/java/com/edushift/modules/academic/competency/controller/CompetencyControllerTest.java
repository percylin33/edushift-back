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
import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.CompetencyReorderRequest;
import com.edushift.modules.academic.competency.dto.CompetencyResponse;
import com.edushift.modules.academic.competency.dto.CompetencyResponse.CourseRef;
import com.edushift.modules.academic.competency.dto.CreateCompetencyRequest;
import com.edushift.modules.academic.competency.dto.SeedCompetenciesResponse;
import com.edushift.modules.academic.competency.dto.UpdateCompetencyRequest;
import com.edushift.modules.academic.competency.service.CompetencyService;
import com.edushift.modules.academic.competency.service.CompetencySeedService;
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
    controllers = CompetencyController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
    GlobalExceptionHandler.class,
    com.edushift.config.SecurityConfig.class,
    com.edushift.config.WebConfiguration.class
})
class CompetencyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CompetencyService service;
    @MockitoBean private CompetencySeedService seedService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

    private static final String COURSE_BASE = "/academic/courses/{courseUuid}/competencies";
    private static final String FLAT_BASE = "/academic/competencies";

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

    @Nested
    @DisplayName("GET /academic/courses/{courseUuid}/competencies")
    class ListCompetencies {

        @Test
        @DisplayName("TENANT_ADMIN — 200 with array")
        void happyPath() throws Exception {
            given(service.listCompetencies(any(), any()))
                .willReturn(List.of(
                    new CompetencyListItem(UUID.randomUUID(), "MAT_C1", "Resuelve", 1, true, 2L)));

            mockMvc.perform(get(COURSE_BASE, UUID.randomUUID()).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MAT_C1"));
        }

        @Test
        @DisplayName("anonymous — 401")
        void anonymous() throws Exception {
            mockMvc.perform(get(COURSE_BASE, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("plain auth — 403")
        void forbidden() throws Exception {
            mockMvc.perform(get(COURSE_BASE, UUID.randomUUID()).with(authentication(plainAuth())))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /academic/courses/{courseUuid}/competencies")
    class CreateCompetency {

        @Test
        @DisplayName("TENANT_ADMIN — 201 with ApiResponse")
        void happyPath() throws Exception {
            var id = UUID.randomUUID();
            given(service.createCompetency(any(), any()))
                .willReturn(new CompetencyResponse(id, new CourseRef(UUID.randomUUID(), "MAT", "Mat"),
                    "MAT_C1", "Resuelve", null, 1, true, List.of(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z")));

            mockMvc.perform(post(COURSE_BASE, UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateCompetencyRequest("MAT_C1", "Resuelve", null, 1, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("MAT_C1"));
        }

        @Test
        @DisplayName("code taken — 409")
        void codeTaken() throws Exception {
            given(service.createCompetency(any(), any()))
                .willThrow(new ConflictException("COMPETENCY_CODE_TAKEN", "MAT_C1"));

            mockMvc.perform(post(COURSE_BASE, UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new CreateCompetencyRequest("MAT_C1", "Resuelve", null, 1, true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("COMPETENCY_CODE_TAKEN"));
        }

        @Test
        @DisplayName("missing name — 400")
        void invalidBody() throws Exception {
            mockMvc.perform(post(COURSE_BASE, UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"MAT_C1\"}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PATCH /academic/courses/{courseUuid}/competencies/reorder")
    class ReorderCompetencies {

        @Test
        @DisplayName("TENANT_ADMIN — 200 with ApiResponse array")
        void happyPath() throws Exception {
            var id = UUID.randomUUID();
            given(service.reorderCompetencies(any(), any()))
                .willReturn(List.of(new CompetencyResponse(id,
                    new CourseRef(UUID.randomUUID(), "MAT", "Mat"),
                    "MAT_C1", "Resuelve", null, 1, true, List.of(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z"))));

            var req = new CompetencyReorderRequest(List.of(
                new CompetencyReorderRequest.Item(id, 1)));

            mockMvc.perform(patch(COURSE_BASE + "/reorder", UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("MAT_C1"));
        }
    }

    @Nested
    @DisplayName("POST /academic/courses/{courseUuid}/competencies/seed-defaults")
    class SeedDefaults {

        @Test
        @DisplayName("TENANT_ADMIN — 200 with SeedCompetenciesResponse")
        void happyPath() throws Exception {
            given(seedService.seedForCourse(any()))
                .willReturn(new SeedCompetenciesResponse(true, false, "MAT", 2, 3, List.of()));

            mockMvc.perform(post(COURSE_BASE + "/seed-defaults", UUID.randomUUID())
                    .with(csrf()).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seeded").value(true));
        }
    }

    @Nested
    @DisplayName("GET /academic/competencies/{publicUuid}")
    class GetOne {

        @Test
        @DisplayName("200 with ApiResponse")
        void happyPath() throws Exception {
            var id = UUID.randomUUID();
            given(service.getCompetency(id))
                .willReturn(new CompetencyResponse(id, new CourseRef(UUID.randomUUID(), "MAT", "Mat"),
                    "MAT_C1", "Resuelve", null, 1, true, List.of(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z")));

            mockMvc.perform(get(FLAT_BASE + "/{id}", id).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("MAT_C1"));
        }

        @Test
        @DisplayName("unknown — 404")
        void notFound() throws Exception {
            var id = UUID.randomUUID();
            given(service.getCompetency(id))
                .willThrow(new ResourceNotFoundException("Competency", id));

            mockMvc.perform(get(FLAT_BASE + "/{id}", id).with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /academic/competencies/{publicUuid}")
    class Update {

        @Test
        @DisplayName("200 with ApiResponse")
        void happyPath() throws Exception {
            var id = UUID.randomUUID();
            given(service.updateCompetency(eq(id), any()))
                .willReturn(new CompetencyResponse(id, new CourseRef(UUID.randomUUID(), "MAT", "Mat"),
                    "MAT_C1", "Updated", null, 1, true, List.of(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z")));

            mockMvc.perform(put(FLAT_BASE + "/{id}", id)
                    .with(csrf()).with(authentication(adminAuth()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new UpdateCompetencyRequest(null, "Updated", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"));
        }
    }

    @Nested
    @DisplayName("DELETE /academic/competencies/{publicUuid}")
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
            org.mockito.BDDMockito.willThrow(new ResourceNotFoundException("Competency", id))
                .given(service).deleteCompetency(id);

            mockMvc.perform(delete(FLAT_BASE + "/{id}", id)
                    .with(csrf()).with(authentication(adminAuth())))
                .andExpect(status().isNotFound());
        }
    }
}
