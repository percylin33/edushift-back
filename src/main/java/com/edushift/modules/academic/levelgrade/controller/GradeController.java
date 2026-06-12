package com.edushift.modules.academic.levelgrade.controller;

import com.edushift.modules.academic.levelgrade.dto.CreateGradeRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeReorderRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeResponse;
import com.edushift.modules.academic.levelgrade.dto.UpdateGradeRequest;
import com.edushift.modules.academic.levelgrade.service.GradeService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code Grade} aggregate (Sprint 4 — BE-4.2),
 * scoped under a parent {@code AcademicLevel}.
 *
 * <h3>Endpoints (under {@code /api/v1/academic/levels/{levelUuid}/grades})</h3>
 * <table>
 *   <caption>Grade endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                          </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@code List<}{@link GradeResponse}{@code >}</td></tr>
 *   <tr><td>POST  </td><td>/                          </td><td>TENANT_ADMIN</td>
 *       <td>{@link GradeResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{gradeUuid}               </td><td>TENANT_ADMIN</td>
 *       <td>{@link GradeResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{gradeUuid}               </td><td>TENANT_ADMIN</td>
 *       <td>204</td></tr>
 *   <tr><td>PATCH </td><td>/reorder                   </td><td>TENANT_ADMIN</td>
 *       <td>{@code List<}{@link GradeResponse}{@code >} (sorted by new ordinal asc)</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/academic/levels/{levelUuid}/grades")
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Grades",
		description = "Grade catalog under a parent AcademicLevel (per-tenant)")
public class GradeController {

	private final GradeService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "List grades of a level",
			description = "Readable by TEACHER so the attendance manual-"
					+ "fallback picker can cascade Level -> Grade (BE-6.8).")
	public ResponseEntity<List<GradeResponse>> list(@PathVariable UUID levelUuid) {
		return ResponseEntity.ok(service.listGrades(levelUuid));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a grade inside a level (TENANT_ADMIN)",
			description = "409 GRADE_ORDINAL_TAKEN when the ordinal collides "
					+ "with another grade in the same level."
	)
	public ResponseEntity<ApiResponse<GradeResponse>> create(
			@PathVariable UUID levelUuid,
			@Valid @RequestBody CreateGradeRequest request
	) {
		GradeResponse response = service.createGrade(levelUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{gradeUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Update a grade (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<GradeResponse>> update(
			@PathVariable UUID levelUuid,
			@PathVariable UUID gradeUuid,
			@Valid @RequestBody UpdateGradeRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.updateGrade(levelUuid, gradeUuid, request)));
	}

	@DeleteMapping("/{gradeUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a grade (TENANT_ADMIN)",
			description = "BE-4.3 will add 409 GRADE_HAS_SECTIONS when sections "
					+ "are wired in."
	)
	public ResponseEntity<Void> delete(
			@PathVariable UUID levelUuid,
			@PathVariable UUID gradeUuid
	) {
		service.deleteGrade(levelUuid, gradeUuid);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/reorder")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Bulk-reorder the grades of a level (TENANT_ADMIN)",
			description = "Two-phase atomic update: each affected grade is "
					+ "parked at a temporary ordinal first, then assigned its "
					+ "final ordinal. Returns the level's grades sorted by "
					+ "ordinal asc. 409 GRADE_REORDER_INVALID when a payload "
					+ "item references a grade outside the level or has duplicates."
	)
	public ResponseEntity<List<GradeResponse>> reorder(
			@PathVariable UUID levelUuid,
			@Valid @RequestBody GradeReorderRequest request
	) {
		return ResponseEntity.ok(service.reorderGrades(levelUuid, request));
	}
}
