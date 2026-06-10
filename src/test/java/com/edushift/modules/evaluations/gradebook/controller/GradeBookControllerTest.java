package com.edushift.modules.evaluations.gradebook.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCellEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookEvaluationEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookStudentEntry;
import com.edushift.modules.evaluations.gradebook.service.GradeBookService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WebMvc layer for {@link GradeBookController} (Sprint 5B / BE-5B.4).
 *
 * <p>Covers the public surface: RBAC (TENANT_ADMIN + TEACHER allowed,
 * other roles forbidden), the {@code ApiResponse<T>} wrapper, and the
 * 404 path when the assignment is unknown / cross-tenant.</p>
 */
@WebMvcTest(
        controllers = GradeBookController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
        GlobalExceptionHandler.class,
        com.edushift.config.SecurityConfig.class,
        com.edushift.config.WebConfiguration.class
})
class GradeBookControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GradeBookService service;
    @MockitoBean private JwtService jwtService;

    private static final String BASE = "/v1/academic/teacher-assignments";

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

    private static GradeBookResponse stub(UUID assignmentUuid) {
        UUID studentUuid = UUID.randomUUID();
        UUID evalUuid = UUID.randomUUID();
        return new GradeBookResponse(
                assignmentUuid,
                UUID.randomUUID(), "3°A",
                UUID.randomUUID(), "Matemática",
                List.of(new GradeBookStudentEntry(
                        studentUuid, "Ana García",
                        new BigDecimal("14.25"))),
                List.of(new GradeBookEvaluationEntry(
                        evalUuid, "Examen", EvaluationKind.EXAM,
                        EvaluationScale.SCORE_0_20,
                        EvaluationStatus.PUBLISHED,
                        new BigDecimal("1.00"),
                        LocalDate.of(2026, 5, 1))),
                List.of(new GradeBookCellEntry(
                        studentUuid, evalUuid,
                        new BigDecimal("14.25"), null,
                        Instant.parse("2026-05-02T00:00:00Z"))));
    }

    // =========================================================================
    // RBAC
    // =========================================================================

    @Test
    @DisplayName("TENANT_ADMIN → 200 + ApiResponse wrapper + denormalised header")
    void admin() throws Exception {
        UUID assignmentUuid = UUID.randomUUID();
        given(service.buildGradeBook(assignmentUuid))
                .willReturn(stub(assignmentUuid));

        mockMvc.perform(get(BASE + "/" + assignmentUuid + "/gradebook")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentPublicUuid")
                        .value(assignmentUuid.toString()))
                .andExpect(jsonPath("$.data.sectionName").value("3°A"))
                .andExpect(jsonPath("$.data.courseName").value("Matemática"))
                .andExpect(jsonPath("$.data.students[0].weightedAverage").value(14.25))
                .andExpect(jsonPath("$.data.evaluations[0].name").value("Examen"))
                .andExpect(jsonPath("$.data.cells[0].score").value(14.25));
    }

    @Test
    @DisplayName("TEACHER → 200 (RBAC allows TEACHER too)")
    void teacher() throws Exception {
        UUID assignmentUuid = UUID.randomUUID();
        given(service.buildGradeBook(assignmentUuid))
                .willReturn(stub(assignmentUuid));

        mockMvc.perform(get(BASE + "/" + assignmentUuid + "/gradebook")
                        .with(authentication(teacherAuth())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("plain user without role → 403")
    void forbidden() throws Exception {
        UUID assignmentUuid = UUID.randomUUID();

        mockMvc.perform(get(BASE + "/" + assignmentUuid + "/gradebook")
                        .with(authentication(plainAuth())))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Service-thrown errors
    // =========================================================================

    @Test
    @DisplayName("unknown assignment → 404 RESOURCE_NOT_FOUND")
    void notFound() throws Exception {
        UUID assignmentUuid = UUID.randomUUID();
        given(service.buildGradeBook(assignmentUuid))
                .willThrow(new ResourceNotFoundException(
                        "TeacherAssignment", assignmentUuid));

        mockMvc.perform(get(BASE + "/" + assignmentUuid + "/gradebook")
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
    }
}
