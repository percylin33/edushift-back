package com.edushift.modules.evaluations.gradebook.controller;

import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import com.edushift.modules.evaluations.gradebook.service.GradeBookService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the on-the-fly grade book aggregate
 * (Sprint 5B / BE-5B.4 / ADR-5B.9).
 *
 * <p>Lives under {@code /v1/academic/teacher-assignments/{uuid}/gradebook}
 * — the assignment is the natural anchor (one teacher × one section ×
 * one course × one period). Mounting it under a separate controller
 * (rather than extending {@code TeacherAssignmentController} or
 * {@code EvaluationController}) keeps the read path independent: the
 * grade book has its own validation surface, its own DTOs, and its
 * own DEBT-EVAL-N follow-up plan (paging + materialisation).</p>
 *
 * <h3>Endpoint</h3>
 * <table>
 *   <caption>Grade book endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET</td>
 *       <td>/academic/teacher-assignments/{publicUuid}/gradebook</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link GradeBookResponse}</td></tr>
 * </table>
 */
@RestController
@RequestMapping
@Validated
@RequiredArgsConstructor
@Tag(name = "Grade Book",
        description = "On-the-fly Student × Evaluation matrix per "
                + "TeacherAssignment (Sprint 5B — BE-5B.4)")
public class GradeBookController {

    private final GradeBookService service;

    @GetMapping("/academic/teacher-assignments/{publicUuid}/gradebook")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(
            summary = "Get the grade book for a teacher assignment "
                    + "(TENANT_ADMIN, TEACHER)",
            description = "Returns three lists driving the matrix: "
                    + "students (rows, with weighted average), "
                    + "evaluations (columns, metadata only) and "
                    + "cells (sparse intersections with score/literal). "
                    + "An assignment with no enrolled students or no "
                    + "evaluations returns 200 with empty arrays — the "
                    + "FE handles the empty state. 404 RESOURCE_NOT_FOUND "
                    + "if the assignment publicUuid is unknown for the "
                    + "tenant (incl. cross-tenant)."
    )
    public ResponseEntity<ApiResponse<GradeBookResponse>> getGradeBook(
            @PathVariable UUID publicUuid
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.buildGradeBook(publicUuid)));
    }
}
