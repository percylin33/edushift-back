package com.edushift.modules.evaluations.evaluationrubric.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.evaluations.controller.EvaluationController;
import com.edushift.modules.evaluations.error.EvaluationErrorCodes;
import com.edushift.modules.evaluations.evaluationrubric.dto.AttachRubricRequest;
import com.edushift.modules.evaluations.evaluationrubric.service.EvaluationRubricService;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.service.EvaluationService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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

/**
 * WebMvc layer for the rubric-association endpoints exposed by
 * {@link EvaluationController} (Sprint 5B / BE-5B.4).
 *
 * <p>Covers RBAC, success paths, the no-rubric-attached
 * {@code EVAL_RUBRIC_NOT_SET} 404 (distinct from the generic
 * {@code RESOURCE_NOT_FOUND}), and Bean Validation on the attach
 * payload.</p>
 */
@WebMvcTest(
        controllers = EvaluationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
        GlobalExceptionHandler.class,
        com.edushift.config.SecurityConfig.class,
        com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class EvaluationRubricEndpointsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // The controller depends on both services; we mock both even though
    // this test only exercises the rubric-link endpoints, so the bean
    // graph wires up cleanly.
    @MockitoBean private EvaluationService evaluationService;
    @MockitoBean private EvaluationRubricService rubricLinkService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

    private static final String BASE = "/v1/academic/evaluations";

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // =========================================================================
    // Auth fixtures
    // =========================================================================

    private static JwtAuthenticationToken adminAuth() {
        return roleAuth("ROLE_TENANT_ADMIN", "admin@acme.test");
    }

    private static JwtAuthenticationToken teacherAuth() {
        return roleAuth("ROLE_TEACHER", "teacher@acme.test");
    }

    private static JwtAuthenticationToken roleAuth(String role, String email) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "acme", email);
        return new JwtAuthenticationToken(principal, "fake.token",
                List.of(new SimpleGrantedAuthority(role)));
    }

    private static JwtAuthenticationToken plainAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "acme", "user@acme.test");
        return new JwtAuthenticationToken(principal, "fake.token",
                List.<GrantedAuthority>of());
    }

    private static RubricResponse stubRubric() {
        return new RubricResponse(
                UUID.randomUUID(), "Rúbrica X", "desc",
                List.of(), List.of(),
                Boolean.FALSE, null, Boolean.TRUE,
                Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-10T00:00:00Z"));
    }

    // =========================================================================
    // POST /rubric (attach)
    // =========================================================================

    @Test
    @DisplayName("attach with TENANT_ADMIN → 200 + ApiResponse wrapper")
    void attachAdmin() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        UUID rubricUuid = UUID.randomUUID();
        given(rubricLinkService.attachRubric(eq(evalUuid),
                any(AttachRubricRequest.class)))
                .willReturn(stubRubric());

        mockMvc.perform(post(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AttachRubricRequest(rubricUuid.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Rúbrica X"));
    }

    @Test
    @DisplayName("attach with TEACHER → 200 (RBAC allows TEACHER too)")
    void attachTeacher() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        UUID rubricUuid = UUID.randomUUID();
        given(rubricLinkService.attachRubric(eq(evalUuid),
                any(AttachRubricRequest.class)))
                .willReturn(stubRubric());

        mockMvc.perform(post(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(teacherAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AttachRubricRequest(rubricUuid.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("attach without authority → 403")
    void attachForbidden() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        UUID rubricUuid = UUID.randomUUID();

        mockMvc.perform(post(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(plainAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AttachRubricRequest(rubricUuid.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("attach without rubricPublicUuid → 400 (Bean Validation)")
    void attachMissingPayload() throws Exception {
        UUID evalUuid = UUID.randomUUID();

        mockMvc.perform(post(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AttachRubricRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("attach with cross-tenant rubric (mocked) → 404 RESOURCE_NOT_FOUND")
    void attachCrossTenant() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        UUID rubricUuid = UUID.randomUUID();
        given(rubricLinkService.attachRubric(eq(evalUuid),
                any(AttachRubricRequest.class)))
                .willThrow(new ResourceNotFoundException("Rubric", rubricUuid));

        mockMvc.perform(post(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AttachRubricRequest(rubricUuid.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // GET /rubric
    // =========================================================================

    @Test
    @DisplayName("get attached rubric — happy path 200 + ApiResponse")
    void getHappy() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        given(rubricLinkService.getAttachedRubric(evalUuid)).willReturn(stubRubric());

        mockMvc.perform(get(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Rúbrica X"));
    }

    @Test
    @DisplayName("get with no rubric attached → 404 EVAL_RUBRIC_NOT_SET "
            + "(distinct from RESOURCE_NOT_FOUND)")
    void getNoRubric() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        given(rubricLinkService.getAttachedRubric(evalUuid))
                .willThrow(new NotFoundException(
                        EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET,
                        "no rubric"));

        mockMvc.perform(get(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code")
                        .value(EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET));
    }

    // =========================================================================
    // DELETE /rubric (detach)
    // =========================================================================

    @Test
    @DisplayName("detach happy path → 204")
    void detachHappy() throws Exception {
        UUID evalUuid = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("detach with no rubric attached → 404 EVAL_RUBRIC_NOT_SET")
    void detachNoRubric() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        doThrow(new NotFoundException(
                EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET, "no rubric"))
                .when(rubricLinkService).detachRubric(evalUuid);

        mockMvc.perform(delete(BASE + "/" + evalUuid + "/rubric")
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code")
                        .value(EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET));
    }
}
