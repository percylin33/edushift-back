package com.edushift.modules.evaluations.graderecord.controller;

import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.CreateGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordFilters;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordListItem;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.UpdateGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.service.GradeRecordService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code GradeRecord} aggregate (Sprint 5B / BE-5B.3).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>GradeRecord endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/academic/evaluations/{uuid}/grade-records
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@code List<}{@link GradeRecordListItem}{@code >}</td></tr>
 *   <tr><td>POST  </td><td>/academic/evaluations/{uuid}/grade-records
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link GradeRecordResponse} (200/201 — upsert semantics)</td></tr>
 *   <tr><td>POST  </td><td>/academic/evaluations/{uuid}/grade-records/bulk
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link BulkGradeRecordResponse} (200, atomic)</td></tr>
 *   <tr><td>GET   </td><td>/academic/grade-records/{publicUuid}
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link GradeRecordResponse}</td></tr>
 *   <tr><td>PUT   </td><td>/academic/grade-records/{publicUuid}
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link GradeRecordResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/academic/grade-records/{publicUuid}
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>204</td></tr>
 * </table>
 *
 * <p>The two-root layout mirrors the sibling {@code EvaluationController}:
 * "list / register / bulk-register" are nested under the parent evaluation
 * because that scope is needed to validate scale and lifecycle, while
 * "get / update / delete" use the flat {@code /academic/grade-records/{uuid}}
 * because the FE has the grade UUID at that point.</p>
 */
@RestController
@RequestMapping
@Validated
@RequiredArgsConstructor
@Tag(name = "GradeRecords",
        description = "Score / qualitative literal registered for a "
                + "(student, evaluation) pair. Upsert + atomic bulk + "
                + "lifecycle gate (DRAFT/PUBLISHED writeable, CLOSED "
                + "read-only).")
public class GradeRecordController {

    private final GradeRecordService service;

    // -------------------------------------------------------------------
    // Nested under the parent evaluation
    // -------------------------------------------------------------------

    @GetMapping("/academic/evaluations/{evaluationUuid}/grade-records")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "List grade records for an evaluation",
            description = "Returns all grades registered against the "
                    + "given evaluation. Optional filters narrow by "
                    + "student, section (via JOIN), or active flag.")
    public ResponseEntity<List<GradeRecordListItem>> list(
            @PathVariable UUID evaluationUuid,
            @RequestParam(name = "studentPublicUuid", required = false)
            @Parameter(description = "Restrict to grades of a single student")
            UUID studentPublicUuid,
            @RequestParam(name = "sectionPublicUuid", required = false)
            @Parameter(description = "Restrict to grades whose evaluation"
                    + "'s assignment belongs to this section")
            UUID sectionPublicUuid,
            @RequestParam(name = "isActive", required = false)
            Boolean isActive) {
        GradeRecordFilters filters = new GradeRecordFilters(
                studentPublicUuid, sectionPublicUuid, isActive);
        return ResponseEntity.ok(service.listGrades(evaluationUuid, filters));
    }

    @PostMapping("/academic/evaluations/{evaluationUuid}/grade-records")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "Upsert a grade record",
            description = "Creates a new grade for the (evaluation, "
                    + "student) pair, or updates the existing row if "
                    + "one is already on file (idempotent upsert per "
                    + "ADR-5B.5).")
    public ResponseEntity<ApiResponse<GradeRecordResponse>> upsert(
            @PathVariable UUID evaluationUuid,
            @Valid @RequestBody CreateGradeRecordRequest request) {
        GradeRecordResponse response = service.upsertGrade(evaluationUuid, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/academic/evaluations/{evaluationUuid}/grade-records/bulk")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "Atomic bulk upsert of grade records",
            description = "Validates every row in the payload up-front "
                    + "and persists them in a single transaction. One "
                    + "invalid row aborts the whole batch with "
                    + "GRADE_BULK_INVALID_ROW (409) — partial saves are "
                    + "impossible.")
    public ResponseEntity<ApiResponse<BulkGradeRecordResponse>> bulk(
            @PathVariable UUID evaluationUuid,
            @Valid @RequestBody BulkGradeRecordRequest request) {
        BulkGradeRecordResponse response = service.bulkUpsert(evaluationUuid, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // -------------------------------------------------------------------
    // Flat endpoints by publicUuid
    // -------------------------------------------------------------------

    @GetMapping("/academic/grade-records/{publicUuid}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "Get a grade record by its public UUID")
    public ResponseEntity<ApiResponse<GradeRecordResponse>> getOne(
            @PathVariable UUID publicUuid) {
        return ResponseEntity.ok(ApiResponse.ok(service.getGrade(publicUuid)));
    }

    @PutMapping("/academic/grade-records/{publicUuid}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "Patch an existing grade record",
            description = "Patch semantics: any non-null field replaces "
                    + "the existing value; null fields are preserved. "
                    + "The (evaluation, student) pair is immutable.")
    public ResponseEntity<ApiResponse<GradeRecordResponse>> update(
            @PathVariable UUID publicUuid,
            @Valid @RequestBody UpdateGradeRecordRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.updateGrade(publicUuid, request)));
    }

    @DeleteMapping("/academic/grade-records/{publicUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "Soft-delete a grade record",
            description = "Sets deleted=true + deleted_at=NOW() on the "
                    + "row. Rejected if the parent evaluation is CLOSED "
                    + "(GRADE_EVAL_CLOSED, 409).")
    public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
        service.deleteGrade(publicUuid);
        return ResponseEntity.noContent().build();
    }
}
