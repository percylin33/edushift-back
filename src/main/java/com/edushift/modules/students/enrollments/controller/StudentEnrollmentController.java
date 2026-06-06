package com.edushift.modules.students.enrollments.controller;

import com.edushift.modules.students.enrollments.dto.CreateEnrollmentRequest;
import com.edushift.modules.students.enrollments.dto.EnrollmentListItem;
import com.edushift.modules.students.enrollments.dto.EnrollmentResponse;
import com.edushift.modules.students.enrollments.dto.SectionStudentRosterItem;
import com.edushift.modules.students.enrollments.dto.WithdrawEnrollmentRequest;
import com.edushift.modules.students.enrollments.service.StudentEnrollmentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code students.enrollments} sub-module.
 * Sprint 4 / BE-4.8.
 *
 * <h3>Endpoints (all under {@code /v1})</h3>
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>POST  </td><td>/students/&#123;studentUuid&#125;/enrollments</td><td>Create enrollment</td></tr>
 *   <tr><td>GET   </td><td>/students/&#123;studentUuid&#125;/enrollments</td><td>Full enrollment timeline</td></tr>
 *   <tr><td>POST  </td><td>/enrollments/&#123;publicUuid&#125;/withdraw  </td><td>Soft-end enrollment</td></tr>
 *   <tr><td>GET   </td><td>/academic/sections/&#123;sectionUuid&#125;/students</td><td>Active section roster</td></tr>
 * </table>
 *
 * <p>All endpoints require {@code TENANT_ADMIN} for now. Sprint 5 will
 * relax the section-roster GET to also allow {@code TEACHER} once the
 * permission audit lands.</p>
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Student enrollments",
		description = "Per-year placement of a student into a section")
public class StudentEnrollmentController {

	private final StudentEnrollmentService service;

	// =========================================================================
	// /students/{uuid}/enrollments
	// =========================================================================

	@PostMapping("/students/{studentUuid}/enrollments")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Enroll a student in a section for a given year",
			description = "Validations (in order): section.year must equal "
					+ "request.year, enrolledAt must fall inside the year "
					+ "window, no other ACTIVE enrollment exists for the "
					+ "same (student, year). Errors: 409 ENROLLMENT_YEAR_MISMATCH, "
					+ "409 ENROLLMENT_DATE_OUT_OF_YEAR, 409 STUDENT_ALREADY_ENROLLED, "
					+ "404 RESOURCE_NOT_FOUND."
	)
	public ResponseEntity<ApiResponse<EnrollmentResponse>> create(
			@PathVariable UUID studentUuid,
			@Valid @RequestBody CreateEnrollmentRequest request
	) {
		EnrollmentResponse response = service.createEnrollment(studentUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@GetMapping("/students/{studentUuid}/enrollments")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List the full enrollment history of a student",
			description = "Returns ACTIVE and terminal rows ordered by "
					+ "enrolledAt descending."
	)
	public ResponseEntity<List<EnrollmentListItem>> listForStudent(
			@PathVariable UUID studentUuid
	) {
		return ResponseEntity.ok(service.listForStudent(studentUuid));
	}

	// =========================================================================
	// /enrollments/{uuid}/withdraw
	// =========================================================================

	@PostMapping("/enrollments/{publicUuid}/withdraw")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-end an enrollment",
			description = "Transitions the row to WITHDRAWN, TRANSFERRED or "
					+ "GRADUATED. Idempotent: re-issuing on a terminal row "
					+ "returns the current snapshot. Errors: "
					+ "400 INVALID_WITHDRAW_STATUS, 400 VALIDATION_ERROR, "
					+ "404 RESOURCE_NOT_FOUND."
	)
	public ResponseEntity<ApiResponse<EnrollmentResponse>> withdraw(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody WithdrawEnrollmentRequest request
	) {
		EnrollmentResponse response = service.withdrawEnrollment(publicUuid, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	// =========================================================================
	// /academic/sections/{uuid}/students (reverse view)
	// =========================================================================

	@GetMapping("/academic/sections/{sectionUuid}/students")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List the currently active students of a section",
			description = "Roster ordered alphabetically by last name. Only "
					+ "ACTIVE enrollments are returned (transferred / "
					+ "graduated rows are part of the student's history "
					+ "but not the live roster)."
	)
	public ResponseEntity<List<SectionStudentRosterItem>> listRoster(
			@PathVariable UUID sectionUuid
	) {
		return ResponseEntity.ok(service.listRoster(sectionUuid));
	}
}
