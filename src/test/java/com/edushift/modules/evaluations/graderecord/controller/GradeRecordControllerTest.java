package com.edushift.modules.evaluations.graderecord.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.CreateGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.error.GradeRecordErrorCodes;
import com.edushift.modules.evaluations.graderecord.service.GradeRecordService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
 * Controller-layer tests for {@link GradeRecordController}. Validates the
 * web wiring (RBAC, payload binding, status codes, error JSON shape).
 *
 * <p>The service is fully mocked; the actual business rules are exercised
 * by {@link com.edushift.modules.evaluations.graderecord.service.impl.GradeRecordServiceImplTest}
 * and {@code GradeRecordTenantIsolationIT}.
 */
@WebMvcTest(
        controllers = GradeRecordController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
        GlobalExceptionHandler.class,
        com.edushift.config.SecurityConfig.class,
        com.edushift.config.WebConfiguration.class
})
class GradeRecordControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private GradeRecordService service;
    @MockitoBean private JwtService jwtService;

    private static final String EVAL_BASE = "/v1/academic/evaluations";
    private static final String GRADE_FLAT_BASE = "/v1/academic/grade-records";

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static JwtAuthenticationToken adminAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(),
                "acme", "admin@acme.test");
        return new JwtAuthenticationToken(principal, "fake.token",
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
    }

    private static JwtAuthenticationToken teacherAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(),
                "acme", "teacher@acme.test");
        return new JwtAuthenticationToken(principal, "fake.token",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    private static JwtAuthenticationToken plainAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(),
                "acme", "user@acme.test");
        return new JwtAuthenticationToken(principal, "fake.token",
                List.<GrantedAuthority>of());
    }

    private static GradeRecordResponse stubResponse(UUID studentUuid) {
        return new GradeRecordResponse(
                UUID.randomUUID(),
                new GradeRecordResponse.EvaluationRef(
                        UUID.randomUUID(), "Examen Bimestre I",
                        EvaluationScale.SCORE_0_20, EvaluationStatus.PUBLISHED),
                new GradeRecordResponse.StudentRef(
                        studentUuid, "Ana", "García", null),
                new BigDecimal("17.50"), null, null,
                Instant.parse("2026-06-10T05:00:00Z"),
                UUID.randomUUID(), Boolean.TRUE,
                Instant.parse("2026-06-10T05:00:00Z"),
                Instant.parse("2026-06-10T05:00:00Z"));
    }

    // -------------------------------------------------------------------
    // Positive paths (auth + validation + happy)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("upsert with TENANT_ADMIN → 200 + ApiResponse wrapper")
    void upsertAdmin() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        UUID studentUuid = UUID.randomUUID();
        given(service.upsertGrade(eq(evalUuid), any(CreateGradeRecordRequest.class)))
                .willReturn(stubResponse(studentUuid));

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateGradeRecordRequest(
                                studentUuid.toString(),
                                new BigDecimal("17.50"), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.score").value(17.50));
    }

    @Test
    @DisplayName("upsert with TEACHER → 200 (RBAC allows TEACHER too)")
    void upsertTeacher() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        UUID studentUuid = UUID.randomUUID();
        given(service.upsertGrade(eq(evalUuid), any(CreateGradeRecordRequest.class)))
                .willReturn(stubResponse(studentUuid));

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records")
                        .with(authentication(teacherAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateGradeRecordRequest(
                                studentUuid.toString(),
                                new BigDecimal("17.50"), null, null))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("upsert without authority → 403")
    void upsertForbidden() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        UUID studentUuid = UUID.randomUUID();

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records")
                        .with(authentication(plainAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateGradeRecordRequest(
                                studentUuid.toString(),
                                new BigDecimal("17.50"), null, null))))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------

    @Test
    @DisplayName("upsert with score = 21 → 400 from Bean Validation")
    void upsertScoreOutOfRange() throws Exception {
        UUID evalUuid = UUID.randomUUID();

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateGradeRecordRequest(
                                UUID.randomUUID().toString(),
                                new BigDecimal("21.00"), null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("upsert without studentPublicUuid → 400 (Bean Validation)")
    void upsertMissingStudent() throws Exception {
        UUID evalUuid = UUID.randomUUID();

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateGradeRecordRequest(
                                "", new BigDecimal("15.00"), null, null))))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------
    // Service-thrown errors (mapped to status by GlobalExceptionHandler)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("upsert with CLOSED evaluation → 409 GRADE_EVAL_CLOSED")
    void upsertEvaluationClosed() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        given(service.upsertGrade(eq(evalUuid), any(CreateGradeRecordRequest.class)))
                .willThrow(new ConflictException(
                        GradeRecordErrorCodes.GRADE_EVAL_CLOSED,
                        "Evaluation " + evalUuid + " is CLOSED"));

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateGradeRecordRequest(
                                UUID.randomUUID().toString(),
                                new BigDecimal("17.50"), null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code")
                        .value(GradeRecordErrorCodes.GRADE_EVAL_CLOSED));
    }

    @Test
    @DisplayName("upsert with student not enrolled → 409 GRADE_STUDENT_NOT_ENROLLED")
    void upsertStudentNotEnrolled() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        given(service.upsertGrade(eq(evalUuid), any(CreateGradeRecordRequest.class)))
                .willThrow(new ConflictException(
                        GradeRecordErrorCodes.GRADE_STUDENT_NOT_ENROLLED,
                        "not enrolled"));

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateGradeRecordRequest(
                                UUID.randomUUID().toString(),
                                new BigDecimal("17.50"), null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code")
                        .value(GradeRecordErrorCodes.GRADE_STUDENT_NOT_ENROLLED));
    }

    @Test
    @DisplayName("upsert with shape mismatch → 400 GRADE_VALUE_SHAPE_MISMATCH")
    void upsertShapeMismatch() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        given(service.upsertGrade(eq(evalUuid), any(CreateGradeRecordRequest.class)))
                .willThrow(new BadRequestException(
                        GradeRecordErrorCodes.GRADE_VALUE_SHAPE_MISMATCH,
                        "shape mismatch"));

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateGradeRecordRequest(
                                UUID.randomUUID().toString(),
                                null, "AD", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code")
                        .value(GradeRecordErrorCodes.GRADE_VALUE_SHAPE_MISMATCH));
    }

    // -------------------------------------------------------------------
    // Bulk
    // -------------------------------------------------------------------

    @Test
    @DisplayName("bulk happy path → 200 + ApiResponse with counts")
    void bulkHappy() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        UUID studentA = UUID.randomUUID();
        UUID studentB = UUID.randomUUID();
        given(service.bulkUpsert(eq(evalUuid), any(BulkGradeRecordRequest.class)))
                .willReturn(new BulkGradeRecordResponse(
                        2, 2, 0,
                        List.of(stubResponse(studentA), stubResponse(studentB))));

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records/bulk")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BulkGradeRecordRequest(List.of(
                                new CreateGradeRecordRequest(
                                        studentA.toString(),
                                        new BigDecimal("17.50"), null, null),
                                new CreateGradeRecordRequest(
                                        studentB.toString(),
                                        new BigDecimal("14.00"), null, null))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.created").value(2));
    }

    @Test
    @DisplayName("bulk with invalid row → 409 GRADE_BULK_INVALID_ROW")
    void bulkInvalidRow() throws Exception {
        UUID evalUuid = UUID.randomUUID();
        given(service.bulkUpsert(eq(evalUuid), any(BulkGradeRecordRequest.class)))
                .willThrow(new ConflictException(
                        GradeRecordErrorCodes.GRADE_BULK_INVALID_ROW,
                        "Row 1 is invalid: shape mismatch"));

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records/bulk")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BulkGradeRecordRequest(List.of(
                                new CreateGradeRecordRequest(
                                        UUID.randomUUID().toString(),
                                        new BigDecimal("17.50"), null, null))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code")
                        .value(GradeRecordErrorCodes.GRADE_BULK_INVALID_ROW));
    }

    @Test
    @DisplayName("bulk with empty records → 400 (Bean Validation @NotEmpty)")
    void bulkEmptyRecords() throws Exception {
        UUID evalUuid = UUID.randomUUID();

        mockMvc.perform(post(EVAL_BASE + "/" + evalUuid + "/grade-records/bulk")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BulkGradeRecordRequest(List.of()))))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------
    // Get / Delete by publicUuid
    // -------------------------------------------------------------------

    @Test
    @DisplayName("getOne unknown → 404 RESOURCE_NOT_FOUND")
    void getOneUnknown() throws Exception {
        UUID uuid = UUID.randomUUID();
        given(service.getGrade(uuid))
                .willThrow(new ResourceNotFoundException("GradeRecord", uuid));

        mockMvc.perform(get(GRADE_FLAT_BASE + "/" + uuid)
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("delete OK → 204 No Content")
    void deleteOk() throws Exception {
        UUID uuid = UUID.randomUUID();

        mockMvc.perform(delete(GRADE_FLAT_BASE + "/" + uuid)
                        .with(authentication(adminAuth()))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
