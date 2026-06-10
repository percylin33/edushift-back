package com.edushift.modules.attendance.controller;

import com.edushift.modules.attendance.dto.AttendanceRecordResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionResponse;
import com.edushift.modules.attendance.dto.CheckInRequest;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.dto.UpdateRecordRequest;
import com.edushift.modules.attendance.service.AttendanceService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

	@PostMapping(value = "/v1/attendance/sessions",
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

	@PatchMapping(value = "/v1/attendance/sessions/{publicUuid}/close",
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

	// =====================================================================
	// Check-in / records
	// =====================================================================

	@PostMapping(value = "/v1/attendance/sessions/{publicUuid}/check-in",
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

	@GetMapping(value = "/v1/attendance/sessions/{publicUuid}/records",
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

	@PutMapping(value = "/v1/attendance/records/{publicUuid}",
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
