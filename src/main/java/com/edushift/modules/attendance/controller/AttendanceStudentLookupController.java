package com.edushift.modules.attendance.controller;

import com.edushift.modules.attendance.dto.AttendanceStudentLookupItem;
import com.edushift.modules.attendance.service.AttendanceStudentLookupService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the attendance student-lookup endpoint
 * (Sprint 6 / BE-6.8 — manual fallback picker).
 *
 * <p>Kept in its own controller (vs. extending
 * {@link AttendanceController}) so the surface area exposed to
 * {@code TEACHER} stays small and auditable. The broader
 * {@code /students} endpoints remain {@code TENANT_ADMIN}-only.
 *
 * <h3>Endpoint</h3>
 * <pre>
 *   GET /v1/attendance/students/lookup
 *     ?q=&amp;levelPublicUuid=&amp;gradePublicUuid=&amp;sectionPublicUuid=
 *     &amp;page=0&amp;size=20
 * </pre>
 *
 * <p>All filters are optional and AND-combined. Pagination follows
 * Spring Data convention; the service hard-caps {@code size} at 50.
 */
@RestController
@RequestMapping
@Validated
@RequiredArgsConstructor
@Tag(name = "Attendance student lookup",
		description = "TEACHER-accessible student picker used by the "
				+ "manual check-in fallback. Returns a lean projection "
				+ "(no email, no birthDate) restricted to students with "
				+ "an ACTIVE enrollment.")
public class AttendanceStudentLookupController {

	private final AttendanceStudentLookupService service;

	@GetMapping(value = "/attendance/students/lookup",
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Search students for the manual check-in picker",
			description = "Returns paginated, tenant-scoped, ACTIVE-enrolled "
					+ "students matching the supplied filters. The 'q' "
					+ "parameter is a case-insensitive substring against "
					+ "firstName, lastName and documentNumber. Filters by "
					+ "levelPublicUuid / gradePublicUuid / sectionPublicUuid "
					+ "walk through the student's current section.")
	public ResponseEntity<ApiResponse<Page<AttendanceStudentLookupItem>>> lookup(
			@Parameter(description = "Case-insensitive substring match on firstName/lastName/documentNumber")
			@RequestParam(required = false) String q,
			@Parameter(description = "Restrict to students whose ACTIVE section belongs to this academic level")
			@RequestParam(required = false) UUID levelPublicUuid,
			@Parameter(description = "Restrict to students whose ACTIVE section belongs to this grade")
			@RequestParam(required = false) UUID gradePublicUuid,
			@Parameter(description = "Restrict to students currently enrolled in this section")
			@RequestParam(required = false) UUID sectionPublicUuid,
			@Parameter(description = "Spring Data pagination. Defaults: size=20, max=50, sort=lastName ASC")
			@PageableDefault(size = 20) Pageable pageable) {
		AttendanceStudentLookupService.Filter filter =
				new AttendanceStudentLookupService.Filter(
						q, levelPublicUuid, gradePublicUuid, sectionPublicUuid);
		Page<AttendanceStudentLookupItem> result = service.lookup(filter, pageable);
		return ResponseEntity.ok(ApiResponse.ok(result));
	}
}
