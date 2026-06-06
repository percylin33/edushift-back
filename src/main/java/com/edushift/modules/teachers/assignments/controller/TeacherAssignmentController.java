package com.edushift.modules.teachers.assignments.controller;

import com.edushift.modules.teachers.assignments.dto.AssignmentListItem;
import com.edushift.modules.teachers.assignments.dto.AssignmentResponse;
import com.edushift.modules.teachers.assignments.dto.CreateAssignmentRequest;
import com.edushift.modules.teachers.assignments.dto.SectionTeacherItem;
import com.edushift.modules.teachers.assignments.service.TeacherAssignmentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code teachers.assignments} sub-module.
 * Sprint 4 / BE-4.7.
 *
 * <h3>Endpoints (all under {@code /v1})</h3>
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>POST  </td><td>/teachers/&#123;teacherUuid&#125;/assignments</td><td>Create assignment</td></tr>
 *   <tr><td>GET   </td><td>/teachers/&#123;teacherUuid&#125;/assignments</td><td>List teacher's assignments</td></tr>
 *   <tr><td>DELETE</td><td>/assignments/&#123;publicUuid&#125;             </td><td>Soft-end assignment</td></tr>
 *   <tr><td>GET   </td><td>/academic/sections/&#123;sectionUuid&#125;/teachers</td><td>Reverse view</td></tr>
 * </table>
 *
 * <p>All endpoints require {@code TENANT_ADMIN} for now. Sprint 5 will
 * relax the {@code GET} reverse view to also allow {@code TEACHER}
 * (a teacher needs to know which sections they share, but the routing
 * to the corresponding feature flag belongs to the auth/permission
 * audit, not here).</p>
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Teacher assignments",
		description = "Teacher ↔ (section, course, period) M:N assignments")
public class TeacherAssignmentController {

	private final TeacherAssignmentService service;

	// =========================================================================
	// /teachers/{uuid}/assignments
	// =========================================================================

	@PostMapping("/teachers/{teacherUuid}/assignments")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Assign a teacher to a (section, course, period) tuple",
			description = "Validations (in order): teacher employmentStatus must be "
					+ "assignable, section.year must equal period.year, course must "
					+ "be applicable to section's level, no active row for this tuple. "
					+ "Errors: 409 ASSIGNMENT_ALREADY_ACTIVE, 409 ASSIGNMENT_YEAR_MISMATCH, "
					+ "409 COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL, 409 TEACHER_NOT_ACTIVE, "
					+ "404 RESOURCE_NOT_FOUND."
	)
	public ResponseEntity<ApiResponse<AssignmentResponse>> create(
			@PathVariable UUID teacherUuid,
			@Valid @RequestBody CreateAssignmentRequest request
	) {
		AssignmentResponse response = service.createAssignment(teacherUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@GetMapping("/teachers/{teacherUuid}/assignments")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List a teacher's assignments",
			description = "Filters: optional period; optional active flag (default true). "
					+ "Set active=false to include historical (soft-ended) rows."
	)
	public ResponseEntity<List<AssignmentListItem>> listForTeacher(
			@PathVariable UUID teacherUuid,
			@Parameter(description = "Filter by period publicUuid")
			@RequestParam(required = false) UUID periodId,
			@Parameter(description = "When true (default), excludes soft-ended assignments")
			@RequestParam(name = "active", defaultValue = "true") boolean activeOnly
	) {
		return ResponseEntity.ok(
				service.listForTeacher(teacherUuid, periodId, activeOnly));
	}

	// =========================================================================
	// /assignments/{uuid}
	// =========================================================================

	@DeleteMapping("/assignments/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-end an assignment",
			description = "Sets unassigned_at = NOW(). Idempotent: re-issuing on a "
					+ "soft-ended row is a no-op (still 204). Historical row is "
					+ "preserved for grade reports / audit."
	)
	public ResponseEntity<Void> softEnd(@PathVariable UUID publicUuid) {
		service.softEnd(publicUuid);
		return ResponseEntity.noContent().build();
	}

	// =========================================================================
	// /academic/sections/{uuid}/teachers (reverse view)
	// =========================================================================

	@GetMapping("/academic/sections/{sectionUuid}/teachers")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List active teachers assigned to a section",
			description = "Returns the active assignments of the section grouped per "
					+ "teacher + course. Optionally narrowed to a single period."
	)
	public ResponseEntity<List<SectionTeacherItem>> listForSection(
			@PathVariable UUID sectionUuid,
			@Parameter(description = "Filter by period publicUuid")
			@RequestParam(required = false) UUID periodId
	) {
		return ResponseEntity.ok(service.listForSection(sectionUuid, periodId));
	}
}
