package com.edushift.modules.attendance.controller;

import com.edushift.modules.attendance.dto.AttendanceRecordResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionListItemResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionResponse;
import com.edushift.modules.attendance.dto.CheckInRequest;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.dto.ManualCheckInRequest;
import com.edushift.modules.attendance.dto.UpdateRecordRequest;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.service.AttendanceService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code AttendanceSession} +
 * {@code AttendanceRecord} aggregates (Sprint 6 / BE-6.4).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Attendance core endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/v1/attendance/sessions</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link AttendanceSessionResponse} (200 idempotent
 *           hit / 201 fresh)</td></tr>
 *   <tr><td>PATCH</td>
 *       <td>/v1/attendance/sessions/{publicUuid}/close</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link AttendanceSessionResponse} (counts + materialised
 *           ABSENT)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/v1/attendance/sessions/{publicUuid}/check-in</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link AttendanceRecordResponse} (200 idempotent hit / 201
 *           fresh)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/v1/attendance/manual-check-in</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link AttendanceRecordResponse} (200/201; auto-resolves
 *           session by student)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/attendance/sessions/{publicUuid}/records</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@code List<}{@link AttendanceRecordResponse}{@code >}
 *           (real + virtual rows)</td></tr>
 *   <tr><td>PUT</td>
 *       <td>/v1/attendance/records/{publicUuid}</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link AttendanceRecordResponse}</td></tr>
 * </table>
 *
 * <h3>Path layout — flat vs nested</h3>
 * Mirrors the convention used by the GradeRecord / Evaluation
 * controllers (Sprint 5B): writes that are scoped to a parent
 * aggregate stay nested under it ({@code /sessions/{id}/...}), while
 * reads / patches that the FE can address by the leaf id are exposed
 * flat under {@code /records/{id}}.
 *
 * <h3>Idempotency contract</h3>
 * <ul>
 *   <li>{@code POST /sessions} — opening twice with the same
 *       {@code (sectionPublicUuid, occurredOn, slot)} returns the same
 *       row with {@code wasIdempotent=true}. The HTTP status is 200
 *       in that case.</li>
 *   <li>{@code POST /sessions/{id}/check-in} — scanning twice for the
 *       same {@code (session, student)} returns the existing record
 *       with {@code wasIdempotent=true} (ADR-6.3).</li>
 *   <li>{@code PATCH /sessions/{id}/close} — a second close on an
 *       already-CLOSED session returns the existing snapshot, never an
 *       error.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping
@Validated
@RequiredArgsConstructor
@Tag(name = "Attendance",
		description = "Attendance sessions, QR check-in, record roster "
				+ "and manual edits. Idempotent by design (ADR-6.3) "
				+ "and tenant-isolated end-to-end.")
public class AttendanceController {

	private final AttendanceService attendanceService;

	// =====================================================================
	// Sessions
	// =====================================================================

	@PostMapping(value = "/attendance/sessions",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Open an attendance session for a section",
			description = "Idempotent on (sectionPublicUuid, "
					+ "occurredOn, slot): re-opening returns the same "
					+ "ACTIVE row with wasIdempotent=true. Returns 200 "
					+ "in that case, 201 on a fresh insert.")
	public ResponseEntity<ApiResponse<AttendanceSessionResponse>> openSession(
			@Valid @RequestBody CreateSessionRequest request) {
		AttendanceSessionResponse response = attendanceService.openSession(request);
		HttpStatus status = Boolean.TRUE.equals(response.wasIdempotent())
				? HttpStatus.OK : HttpStatus.CREATED;
		log.debug("[attendance-api] openSession status={} session={} idempotent={}",
				status.value(), response.publicUuid(), response.wasIdempotent());
		return ResponseEntity.status(status).body(ApiResponse.ok(response));
	}

	@PatchMapping(value = "/attendance/sessions/{publicUuid}/close",
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Close an attendance session",
			description = "Transitions the session to CLOSED, "
					+ "materialises ABSENT records for unscanned "
					+ "enrolled students (ADR-6.6), and returns the "
					+ "snapshot with the four counts populated. "
					+ "Idempotent: re-closing a CLOSED session returns "
					+ "the existing snapshot.")
	public ResponseEntity<ApiResponse<AttendanceSessionResponse>> closeSession(
			@PathVariable UUID publicUuid) {
		AttendanceSessionResponse response = attendanceService.closeSession(publicUuid);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@GetMapping(value = "/attendance/sessions",
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "List attendance sessions (tenant-scoped, paginated)",
			description = "Drives the /attendance/sessions list page (FE-6.2). "
					+ "All filters are optional and AND-combined: a single day "
					+ "filter is expressed as `from=date&to=date`. Results are "
					+ "ordered by `occurredOn DESC, startsAt DESC`. Cross-tenant "
					+ "leakage is structurally impossible: the controller NEVER "
					+ "accepts `tenantId`; the repository is tenant-scoped by "
					+ "Hibernate's `@TenantId` discriminator (BE-6.7).")
	public ResponseEntity<ApiResponse<Page<AttendanceSessionListItemResponse>>> listSessions(
			@Parameter(description = "Filter by section public UUID")
			@RequestParam(required = false) UUID sectionPublicUuid,
			@Parameter(description = "Inclusive lower bound on `occurredOn` (yyyy-MM-dd)")
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "Inclusive upper bound on `occurredOn` (yyyy-MM-dd)")
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@Parameter(description = "Filter by slot: MORNING / AFTERNOON / EVENING")
			@RequestParam(required = false) AttendanceSessionSlot slot,
			@Parameter(description = "Filter by session status: ACTIVE / CLOSED")
			@RequestParam(required = false) AttendanceSessionStatus status,
			@Parameter(description = "Standard Spring Data pagination "
					+ "(page, size, sort). Defaults: size=20, sort=occurredOn,DESC")
			@PageableDefault(size = 20) Pageable pageable) {
		AttendanceService.ListSessionsFilter filter =
				new AttendanceService.ListSessionsFilter(
						sectionPublicUuid, from, to, slot, status);
		Page<AttendanceSessionListItemResponse> result =
				attendanceService.listSessions(filter, pageable);
		return ResponseEntity.ok(ApiResponse.ok(result));
	}

	// =====================================================================
	// Check-in / records
	// =====================================================================

	@PostMapping(value = "/attendance/sessions/{publicUuid}/check-in",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Register a check-in scan",
			description = "Validates the QR JWT (HS256, typ=attendance), "
					+ "asserts tenant + enrollment + session ACTIVE, "
					+ "computes PRESENT/LATE per tenant config, and "
					+ "persists the record. Idempotent on (session, "
					+ "student): re-scanning returns the existing row "
					+ "with wasIdempotent=true (ADR-6.3).",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(schema = @Schema(implementation = CheckInRequest.class))))
	public ResponseEntity<ApiResponse<AttendanceRecordResponse>> checkIn(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody CheckInRequest request) {
		// The path variable wins — guarding against clients sending a
		// session UUID in the body that doesn't match the URL keeps the
		// idempotency key honest. We rebuild the request to enforce it.
		CheckInRequest effective = new CheckInRequest(
				request.qrToken(),
				publicUuid,
				request.occurredAt(),
				request.forcedStatus());
		AttendanceRecordResponse response = attendanceService.checkIn(effective);
		HttpStatus status = Boolean.TRUE.equals(response.wasIdempotent())
				? HttpStatus.OK : HttpStatus.CREATED;
		log.debug("[attendance-api] checkIn status={} record={} idempotent={}",
				status.value(), response.publicUuid(), response.wasIdempotent());
		return ResponseEntity.status(status).body(ApiResponse.ok(response));
	}

	@PostMapping(value = "/attendance/scan-check-in",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Session-less QR check-in (auto-resolves session)",
			description = "Used by the scanner page (BE-6.8.b) when no "
					+ "attendance session is pre-opened — the canonical "
					+ "'auxiliary at the school entrance' flow. The "
					+ "backend validates the QR exactly like /attendance/"
					+ "sessions/{uuid}/check-in (HS256 + tenant + revoked "
					+ "check) and then resolves the target session from "
					+ "the student's current ACTIVE enrollment, identical "
					+ "to /attendance/manual-check-in. Idempotent on "
					+ "(resolved session, student). "
					+ "Errors: 401 QR_INVALID / 410 QR_EXPIRED / 404 "
					+ "RESOURCE_NOT_FOUND (tenant mismatch) / 422 "
					+ "STUDENT_NO_ACTIVE_ENROLLMENT / 403 FORCED_STATUS_FORBIDDEN.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(schema = @Schema(implementation = com.edushift.modules.attendance.dto.ScanCheckInRequest.class))))
	public ResponseEntity<ApiResponse<AttendanceRecordResponse>> scanCheckIn(
			@Valid @RequestBody com.edushift.modules.attendance.dto.ScanCheckInRequest request) {
		AttendanceRecordResponse response = attendanceService.scanCheckIn(request);
		HttpStatus status = Boolean.TRUE.equals(response.wasIdempotent())
				? HttpStatus.OK : HttpStatus.CREATED;
		log.debug("[attendance-api] scanCheckIn status={} record={} idempotent={}",
				status.value(), response.publicUuid(), response.wasIdempotent());
		return ResponseEntity.status(status).body(ApiResponse.ok(response));
	}

	@PostMapping(value = "/attendance/manual-check-in",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Manual check-in by studentPublicUuid (no QR)",
			description = "Used by the 'auxiliar en la entrada del colegio' "
					+ "flow when the student has no QR card on hand. The "
					+ "backend resolves the target session automatically: "
					+ "looks up the student's current ACTIVE enrollment "
					+ "(section), then findOrOpens an ACTIVE session for "
					+ "(section, occurredOn, slot). occurredOn defaults to "
					+ "today; slot defaults to MORNING before noon and "
					+ "AFTERNOON otherwise (override via the body). "
					+ "Idempotent on (resolved session, student) — same "
					+ "semantics as the QR check-in (ADR-6.3). "
					+ "Errors: 404 RESOURCE_NOT_FOUND when the student is "
					+ "from another tenant; 422 STUDENT_NO_ACTIVE_ENROLLMENT "
					+ "when the student has no current section; "
					+ "403 FORCED_STATUS_FORBIDDEN for non-admin forced status.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(schema = @Schema(implementation = ManualCheckInRequest.class))))
	public ResponseEntity<ApiResponse<AttendanceRecordResponse>> manualCheckIn(
			@Valid @RequestBody ManualCheckInRequest request) {
		AttendanceRecordResponse response = attendanceService.manualCheckIn(request);
		HttpStatus status = Boolean.TRUE.equals(response.wasIdempotent())
				? HttpStatus.OK : HttpStatus.CREATED;
		log.debug("[attendance-api] manualCheckIn status={} record={} idempotent={}",
				status.value(), response.publicUuid(), response.wasIdempotent());
		return ResponseEntity.status(status).body(ApiResponse.ok(response));
	}

	@GetMapping(value = "/attendance/sessions/{publicUuid}/records",
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "List the roster of a session",
			description = "One row per enrolled student. ACTIVE "
					+ "sessions emit virtual rows with status=null for "
					+ "unscanned students; CLOSED sessions emit ABSENT "
					+ "(materialised at close time, ADR-6.6).")
	public ResponseEntity<List<AttendanceRecordResponse>> listRecords(
			@PathVariable UUID publicUuid) {
		return ResponseEntity.ok(attendanceService.listRecords(publicUuid));
	}

	@PutMapping(value = "/attendance/records/{publicUuid}",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Manually edit a record's status / notes",
			description = "Patch semantics — non-null fields replace, "
					+ "null fields are preserved. TEACHER edits are "
					+ "limited to the 24h post-close window (ADR-6.7); "
					+ "TENANT_ADMIN has no window. Status transitions "
					+ "for non-admins are restricted to the per-status "
					+ "allow-list defined on AttendanceRecordStatus.")
	public ResponseEntity<ApiResponse<AttendanceRecordResponse>> updateRecord(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateRecordRequest request) {
		AttendanceRecordResponse response = attendanceService.updateRecord(
				publicUuid, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}
}
