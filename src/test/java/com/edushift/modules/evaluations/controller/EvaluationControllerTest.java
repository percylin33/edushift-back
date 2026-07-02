package com.edushift.modules.evaluations.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.evaluations.dto.CreateEvaluationRequest;
import com.edushift.modules.evaluations.dto.EvaluationResponse;
import com.edushift.modules.evaluations.dto.EvaluationResponse.AssignmentRef;
import com.edushift.modules.evaluations.dto.UpdateEvaluationRequest;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.evaluationrubric.dto.AttachRubricRequest;
import com.edushift.modules.evaluations.evaluationrubric.service.EvaluationRubricService;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.service.EvaluationService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluationController.class)
@Import(GlobalExceptionHandler.class)
class EvaluationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EvaluationService service;
    @MockitoBean private EvaluationRubricService rubricLinkService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private EvaluationResponse stubResponse(UUID publicUuid, EvaluationStatus status) {
        return new EvaluationResponse(
                publicUuid,
                new AssignmentRef(UUID.randomUUID(), "MAT · 5A · Garcia · B1"),
                null, null,
                EvaluationKind.EXAM, "Midterm", "60 minutes",
                new BigDecimal("25.00"),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 8),
                EvaluationScale.SCORE_0_20, status,
                status == EvaluationStatus.PUBLISHED || status == EvaluationStatus.CLOSED
                        ? Instant.parse("2026-05-01T00:00:00Z") : null,
                status == EvaluationStatus.CLOSED
                        ? Instant.parse("2026-06-01T00:00:00Z") : null,
                Boolean.TRUE, 0L,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"));
    }

    // =========================================================================
    // GET /academic/assignments/{uuid}/evaluations
    // =========================================================================

    @Nested
    @DisplayName("GET /academic/assignments/{uuid}/evaluations")
    class ListEvaluations {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 200 with array")
        void happyPath() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            UUID evalUuid = UUID.randomUUID();
            given(service.listEvaluations(eq(assignmentUuid), any()))
                    .willReturn(List.of(new com.edushift.modules.evaluations.dto.EvaluationListItem(
                            evalUuid, EvaluationKind.EXAM, "Midterm",
                            new BigDecimal("25.00"),
                            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 8),
                            EvaluationScale.SCORE_0_20, EvaluationStatus.DRAFT,
                            5L, Boolean.TRUE,
                            Instant.now(), Instant.now())));

            mockMvc.perform(get("/academic/assignments/{u}/evaluations", assignmentUuid))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].publicUuid").value(evalUuid.toString()))
                    .andExpect(jsonPath("$[0].status").value("DRAFT"))
                    .andExpect(jsonPath("$[0].gradeCount").value(5));
        }

        @Test
        @WithMockUser(roles = "TEACHER")
        @DisplayName("TEACHER role is allowed")
        void teacherAllowed() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            given(service.listEvaluations(eq(assignmentUuid), any())).willReturn(List.of());

            mockMvc.perform(get("/academic/assignments/{u}/evaluations", assignmentUuid))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "STUDENT")
        @DisplayName("STUDENT role is forbidden (403)")
        void studentForbidden() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            mockMvc.perform(get("/academic/assignments/{u}/evaluations", assignmentUuid))
                    .andExpect(status().isForbidden());
            then(service).should(never()).listEvaluations(any(), any());
        }

        @Test
        @DisplayName("anonymous → 401")
        void anonymous() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            mockMvc.perform(get("/academic/assignments/{u}/evaluations", assignmentUuid))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("status filter — passthrough")
        void statusFilter() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            given(service.listEvaluations(eq(assignmentUuid), any())).willReturn(List.of());

            mockMvc.perform(get("/academic/assignments/{u}/evaluations?status=PUBLISHED", assignmentUuid))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("unknown assignment → 404 RESOURCE_NOT_FOUND")
        void unknown() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            given(service.listEvaluations(eq(assignmentUuid), any()))
                    .willThrow(new ResourceNotFoundException("TeacherAssignment", assignmentUuid));

            mockMvc.perform(get("/academic/assignments/{u}/evaluations", assignmentUuid))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
        }
    }

    // =========================================================================
    // POST /academic/assignments/{uuid}/evaluations
    // =========================================================================

    @Nested
    @DisplayName("POST /academic/assignments/{uuid}/evaluations")
    class CreateEvaluation {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 201")
        void happyPath() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            UUID evalUuid = UUID.randomUUID();
            given(service.createEvaluation(eq(assignmentUuid), any()))
                    .willReturn(stubResponse(evalUuid, EvaluationStatus.DRAFT));

            var req = new CreateEvaluationRequest(
                    EvaluationKind.EXAM, "Midterm", "60 minutes",
                    new BigDecimal("25.00"),
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 8),
                    EvaluationScale.SCORE_0_20, null, null, null);

            mockMvc.perform(post("/academic/assignments/{u}/evaluations", assignmentUuid)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.publicUuid").value(evalUuid.toString()))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("missing required field → 400 (validation, service never invoked)")
        void missingField() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            var req = new CreateEvaluationRequest(null, null, null,
                    null, null, null, null, null, null, null);

            mockMvc.perform(post("/academic/assignments/{u}/evaluations", assignmentUuid)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            then(service).should(never()).createEvaluation(any(), any());
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("name collision → 409 EVAL_NAME_EXISTS")
        void nameCollision() throws Exception {
            UUID assignmentUuid = UUID.randomUUID();
            given(service.createEvaluation(eq(assignmentUuid), any()))
                    .willThrow(new ConflictException("EVAL_NAME_EXISTS", "duplicate"));

            var req = new CreateEvaluationRequest(
                    EvaluationKind.EXAM, "Midterm", null,
                    new BigDecimal("25.00"),
                    LocalDate.of(2026, 5, 1), null,
                    EvaluationScale.SCORE_0_20, null, null, null);

            mockMvc.perform(post("/academic/assignments/{u}/evaluations", assignmentUuid)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("EVAL_NAME_EXISTS"));
        }
    }

    // =========================================================================
    // GET /academic/evaluations/{publicUuid}
    // =========================================================================

    @Nested
    @DisplayName("GET /academic/evaluations/{publicUuid}")
    class GetEvaluation {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 200")
        void happyPath() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            given(service.getEvaluation(evalUuid)).willReturn(stubResponse(evalUuid, EvaluationStatus.DRAFT));

            mockMvc.perform(get("/academic/evaluations/{u}", evalUuid))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.publicUuid").value(evalUuid.toString()))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"));
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("unknown → 404")
        void unknown() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            given(service.getEvaluation(evalUuid))
                    .willThrow(new ResourceNotFoundException("Evaluation", evalUuid));

            mockMvc.perform(get("/academic/evaluations/{u}", evalUuid))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // PUT /academic/evaluations/{publicUuid}
    // =========================================================================

    @Nested
    @DisplayName("PUT /academic/evaluations/{publicUuid}")
    class UpdateEvaluation {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 200")
        void happyPath() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            given(service.updateEvaluation(eq(evalUuid), any()))
                    .willReturn(stubResponse(evalUuid, EvaluationStatus.PUBLISHED));

            var patch = new UpdateEvaluationRequest(
                    null, "Renamed", null, null, null, null,
                    null, null, null, null);

            mockMvc.perform(put("/academic/evaluations/{u}", evalUuid)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patch)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Midterm"));
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("CLOSED → 409 EVAL_CLOSED")
        void closed() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            given(service.updateEvaluation(eq(evalUuid), any()))
                    .willThrow(new ConflictException("EVAL_CLOSED", "read-only"));

            var patch = new UpdateEvaluationRequest(
                    null, "Renamed", null, null, null, null,
                    null, null, null, null);

            mockMvc.perform(put("/academic/evaluations/{u}", evalUuid)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patch)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("EVAL_CLOSED"));
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("POST /academic/evaluations/{u}/publish")
    class Publish {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("DRAFT → PUBLISHED")
        void happyPath() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            given(service.publishEvaluation(evalUuid))
                    .willReturn(stubResponse(evalUuid, EvaluationStatus.PUBLISHED));

            mockMvc.perform(post("/academic/evaluations/{u}/publish", evalUuid))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("already PUBLISHED → 409 EVAL_ILLEGAL_TRANSITION")
        void alreadyPublished() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            given(service.publishEvaluation(evalUuid))
                    .willThrow(new ConflictException("EVAL_ILLEGAL_TRANSITION", "already"));

            mockMvc.perform(post("/academic/evaluations/{u}/publish", evalUuid))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("EVAL_ILLEGAL_TRANSITION"));
        }
    }

    @Nested
    @DisplayName("POST /academic/evaluations/{u}/close")
    class Close {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("PUBLISHED → CLOSED")
        void happyPath() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            given(service.closeEvaluation(evalUuid))
                    .willReturn(stubResponse(evalUuid, EvaluationStatus.CLOSED));

            mockMvc.perform(post("/academic/evaluations/{u}/close", evalUuid))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("DRAFT → 409 EVAL_ILLEGAL_TRANSITION")
        void fromDraft() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            given(service.closeEvaluation(evalUuid))
                    .willThrow(new ConflictException("EVAL_ILLEGAL_TRANSITION", "draft"));

            mockMvc.perform(post("/academic/evaluations/{u}/close", evalUuid))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("EVAL_ILLEGAL_TRANSITION"));
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    @Nested
    @DisplayName("DELETE /academic/evaluations/{u}")
    class DeleteEvaluation {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 204")
        void happyPath() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            willDoNothing().given(service).deleteEvaluation(evalUuid);

            mockMvc.perform(delete("/academic/evaluations/{u}", evalUuid))
                    .andExpect(status().isNoContent());

            then(service).should(times(1)).deleteEvaluation(evalUuid);
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("has grades → 409 EVAL_HAS_GRADES")
        void hasGrades() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            willThrow(new ConflictException("EVAL_HAS_GRADES", "grades"))
                    .given(service).deleteEvaluation(evalUuid);

            mockMvc.perform(delete("/academic/evaluations/{u}", evalUuid))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("EVAL_HAS_GRADES"));
        }
    }

    // =========================================================================
    // Rubric association
    // =========================================================================

    @Nested
    @DisplayName("POST /academic/evaluations/{u}/rubric")
    class AttachRubric {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 200")
        void happyPath() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            UUID rubricUuid = UUID.randomUUID();
            given(rubricLinkService.attachRubric(eq(evalUuid), any()))
                    .willReturn(new RubricResponse(rubricUuid, "My Rubric", null,
                            List.of(), List.of(), Boolean.FALSE, null, Boolean.TRUE,
                            Instant.now(), Instant.now()));

            var req = new AttachRubricRequest(rubricUuid.toString());
            mockMvc.perform(post("/academic/evaluations/{u}/rubric", evalUuid)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.publicUuid").value(rubricUuid.toString()));
        }
    }

    @Nested
    @DisplayName("GET /academic/evaluations/{u}/rubric")
    class GetAttachedRubric {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 200")
        void happyPath() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            UUID rubricUuid = UUID.randomUUID();
            given(rubricLinkService.getAttachedRubric(evalUuid))
                    .willReturn(new RubricResponse(rubricUuid, "My Rubric", null,
                            List.of(), List.of(), Boolean.FALSE, null, Boolean.TRUE,
                            Instant.now(), Instant.now()));

            mockMvc.perform(get("/academic/evaluations/{u}/rubric", evalUuid))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("My Rubric"));
        }
    }

    @Nested
    @DisplayName("DELETE /academic/evaluations/{u}/rubric")
    class DetachRubric {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 204")
        void happyPath() throws Exception {
            UUID evalUuid = UUID.randomUUID();
            willDoNothing().given(rubricLinkService).detachRubric(evalUuid);

            mockMvc.perform(delete("/academic/evaluations/{u}/rubric", evalUuid))
                    .andExpect(status().isNoContent());

            then(rubricLinkService).should(times(1)).detachRubric(evalUuid);
        }
    }
}