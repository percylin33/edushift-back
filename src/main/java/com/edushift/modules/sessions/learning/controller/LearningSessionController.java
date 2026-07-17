package com.edushift.modules.sessions.learning.controller;

import com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest;
import com.edushift.modules.sessions.learning.dto.LearningSessionFilters;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse;
import com.edushift.modules.sessions.learning.dto.LifecycleRequest;
import com.edushift.modules.sessions.learning.dto.UpdateLearningSessionRequest;
import com.edushift.modules.sessions.learning.entity.SessionStatus;
import com.edushift.modules.sessions.learning.service.LearningSessionService;
import com.edushift.modules.sessions.learning.service.SessionsPdfExportService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code LearningSession} aggregate
 * (Sprint 5A - BE-5A.4).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>LearningSession endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/learning-sessions                                 </td><td>TENANT_ADMIN</td><td>List&lt;{@link LearningSessionListItem}&gt;</td></tr>
 *   <tr><td>POST  </td><td>/learning-sessions                                 </td><td>TENANT_ADMIN</td><td>{@link LearningSessionResponse} (201)</td></tr>
 *   <tr><td>GET   </td><td>/learning-sessions/{publicUuid}                    </td><td>TENANT_ADMIN</td><td>{@link LearningSessionResponse}</td></tr>
 *   <tr><td>PUT   </td><td>/learning-sessions/{publicUuid}                    </td><td>TENANT_ADMIN</td><td>{@link LearningSessionResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/learning-sessions/{publicUuid}                    </td><td>TENANT_ADMIN</td><td>204</td></tr>
 *   <tr><td>POST  </td><td>/learning-sessions/{publicUuid}/start              </td><td>TENANT_ADMIN</td><td>{@link LearningSessionResponse}</td></tr>
 *   <tr><td>POST  </td><td>/learning-sessions/{publicUuid}/complete           </td><td>TENANT_ADMIN</td><td>{@link LearningSessionResponse}</td></tr>
 *   <tr><td>POST  </td><td>/learning-sessions/{publicUuid}/cancel             </td><td>TENANT_ADMIN</td><td>{@link LearningSessionResponse}</td></tr>
 *   <tr><td>GET   </td><td>/teacher-assignments/{a}/sessions                  </td><td>TENANT_ADMIN</td><td>List&lt;{@link LearningSessionListItem}&gt;</td></tr>
 *   <tr><td>GET   </td><td>/academic/units/{u}/sessions                       </td><td>TENANT_ADMIN</td><td>List&lt;{@link LearningSessionListItem}&gt;</td></tr>
 * </table>
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Sessions - Learning Sessions",
		description = "Daily class occurrences anchored on a TeacherAssignment + Unit "
				+ "(Sprint 5A - BE-5A.4)")
public class LearningSessionController {

	private final LearningSessionService service;
	private final SessionsPdfExportService pdfExportService;

	// =========================================================================
	// CRUD
	// =========================================================================

	@GetMapping("/learning-sessions")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List learning sessions with filters (TENANT_ADMIN)",
			description = "Filters are AND-combined and skip-on-null. "
					+ "Sorted by (scheduledDate asc, createdAt asc). "
					+ "400 VALIDATION_ERROR if dateFrom > dateTo."
	)
	public ResponseEntity<List<LearningSessionListItem>> list(
			@Parameter(description = "Filter by teacher publicUuid")
			@RequestParam(name = "teacherId", required = false) UUID teacherUuid,
			@Parameter(description = "Filter by section publicUuid")
			@RequestParam(name = "sectionId", required = false) UUID sectionUuid,
			@Parameter(description = "Filter by unit publicUuid")
			@RequestParam(name = "unitId", required = false) UUID unitUuid,
			@Parameter(description = "Filter by academic period publicUuid")
			@RequestParam(name = "periodId", required = false) UUID periodUuid,
			@Parameter(description = "Filter by lifecycle status")
			@RequestParam(name = "status", required = false) SessionStatus status,
			@Parameter(description = "Inclusive lower bound (yyyy-MM-dd)")
			@RequestParam(name = "dateFrom", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@Parameter(description = "Inclusive upper bound (yyyy-MM-dd)")
			@RequestParam(name = "dateTo", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
	) {
		LearningSessionFilters filters = new LearningSessionFilters(
				teacherUuid, sectionUuid, unitUuid, periodUuid,
				status, dateFrom, dateTo);
		return ResponseEntity.ok(service.list(filters));
	}

	@PostMapping("/learning-sessions")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a learning session (TENANT_ADMIN)",
			description = "400 SESSION_DATE_OUT_OF_PERIOD if scheduledDate is outside "
					+ "the assignment's period window. 400 UNIT_NOT_IN_COURSE / "
					+ "COMPETENCY_NOT_IN_COURSE / CAPACITY_NOT_IN_COURSE for cross-course "
					+ "references. 409 ASSIGNMENT_NOT_ACTIVE if the parent assignment "
					+ "has been soft-ended."
	)
	public ResponseEntity<ApiResponse<LearningSessionResponse>> create(
			@Valid @RequestBody CreateLearningSessionRequest request
	) {
		LearningSessionResponse response = service.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@GetMapping("/learning-sessions/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a learning session by publicUuid (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<LearningSessionResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.get(publicUuid)));
	}

	@GetMapping("/learning-sessions/{publicUuid}/export")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Export a learning session as PDF (BE-18.2)",
			description = "Generates a PDF with the session title, objective, content "
					+ "(activities / materials / observations), competencies, capacities "
					+ "and school header. Returns application/pdf with Content-Disposition."
	)
	public ResponseEntity<byte[]> exportPdf(
			@PathVariable UUID publicUuid,
			@RequestParam(defaultValue = "pdf") String format
	) {
		byte[] pdfBytes = pdfExportService.exportPdf(publicUuid);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDispositionFormData("attachment",
				"sesion-" + publicUuid + ".pdf");
		return ResponseEntity.ok()
				.headers(headers)
				.body(pdfBytes);
	}

	@PutMapping("/learning-sessions/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a learning session (TENANT_ADMIN)",
			description = "Partial-merge. Lifecycle (status / timestamps) cannot be "
					+ "changed here - use /start, /complete, /cancel instead. The "
					+ "teacherAssignment is immutable: to move a session, delete + "
					+ "recreate. 409 SESSION_TRANSITION_INVALID if the session is "
					+ "in a terminal state (COMPLETED / CANCELLED)."
	)
	public ResponseEntity<ApiResponse<LearningSessionResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateLearningSessionRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.update(publicUuid, request)));
	}

	@DeleteMapping("/learning-sessions/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a learning session (TENANT_ADMIN)",
			description = "Future Sprint 6 will reject deletion when attendance "
					+ "records exist (409 SESSION_HAS_ATTENDANCE)."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.delete(publicUuid);
		return ResponseEntity.noContent().build();
	}

	// =========================================================================
	// Lifecycle
	// =========================================================================

	@PostMapping("/learning-sessions/{publicUuid}/start")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Transition PLANNED -> IN_PROGRESS (TENANT_ADMIN)",
			description = "Stamps started_at = now(). 409 SESSION_TRANSITION_INVALID if "
					+ "current status is not PLANNED. 409 SESSION_VERSION_CONFLICT if "
					+ "the entity was modified since the FE loaded it."
	)
	public ResponseEntity<ApiResponse<LearningSessionResponse>> start(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody LifecycleRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.start(publicUuid, request)));
	}

	@PostMapping("/learning-sessions/{publicUuid}/complete")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Transition IN_PROGRESS -> COMPLETED (TENANT_ADMIN)",
			description = "Stamps ended_at = now(). 409 SESSION_TRANSITION_INVALID if "
					+ "current status is not IN_PROGRESS."
	)
	public ResponseEntity<ApiResponse<LearningSessionResponse>> complete(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody LifecycleRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.complete(publicUuid, request)));
	}

	@PostMapping("/learning-sessions/{publicUuid}/cancel")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Cancel a learning session (TENANT_ADMIN)",
			description = "Allowed from PLANNED or IN_PROGRESS; rejected from terminal "
					+ "states (409 SESSION_TRANSITION_INVALID). Optional reason is "
					+ "appended to the session's objective for the audit trail."
	)
	public ResponseEntity<ApiResponse<LearningSessionResponse>> cancel(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody LifecycleRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.cancel(publicUuid, request)));
	}

	// =========================================================================
	// Reverse views
	// =========================================================================

	@GetMapping("/teacher-assignments/{assignmentUuid}/sessions")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List sessions of an assignment (TENANT_ADMIN)",
			description = "Sorted by (scheduledDate asc, createdAt asc). 404 if "
					+ "the assignment publicUuid is unknown / cross-tenant."
	)
	public ResponseEntity<List<LearningSessionListItem>> listByAssignment(
			@PathVariable UUID assignmentUuid
	) {
		return ResponseEntity.ok(service.listByAssignment(assignmentUuid));
	}

	@GetMapping("/academic/units/{unitUuid}/sessions")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List sessions of a unit (TENANT_ADMIN)",
			description = "Drives the 'lessons in this unit' tab on the unit-detail "
					+ "screen. 404 if the unit publicUuid is unknown / cross-tenant."
	)
	public ResponseEntity<List<LearningSessionListItem>> listByUnit(
			@PathVariable UUID unitUuid
	) {
		return ResponseEntity.ok(service.listByUnit(unitUuid));
	}
}
