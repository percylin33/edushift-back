package com.edushift.modules.schedule.timeslot.controller;

import com.edushift.modules.schedule.timeslot.dto.CreateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.dto.ScheduleSlotItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotListItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse;
import com.edushift.modules.schedule.timeslot.dto.UpdateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.service.TimeSlotService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code TimeSlot} aggregate (Sprint 5A — BE-5A.3).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>TimeSlot endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/teacher-assignments/{a}/time-slots          </td><td>TENANT_ADMIN</td><td>{@code List<}{@link TimeSlotListItem}{@code >}</td></tr>
 *   <tr><td>POST  </td><td>/teacher-assignments/{a}/time-slots          </td><td>TENANT_ADMIN</td><td>{@link TimeSlotResponse} (201)</td></tr>
 *   <tr><td>GET   </td><td>/time-slots/{publicUuid}                     </td><td>TENANT_ADMIN</td><td>{@link TimeSlotResponse}</td></tr>
 *   <tr><td>PUT   </td><td>/time-slots/{publicUuid}                     </td><td>TENANT_ADMIN</td><td>{@link TimeSlotResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/time-slots/{publicUuid}                     </td><td>TENANT_ADMIN</td><td>204</td></tr>
 *   <tr><td>GET   </td><td>/teachers/{t}/schedule?periodId=...          </td><td>TENANT_ADMIN</td><td>{@code List<}{@link ScheduleSlotItem}{@code >}</td></tr>
 *   <tr><td>GET   </td><td>/academic/sections/{s}/schedule?periodId=... </td><td>TENANT_ADMIN</td><td>{@code List<}{@link ScheduleSlotItem}{@code >}</td></tr>
 * </table>
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Schedule — Time Slots",
		description = "Weekly recurring schedule per TeacherAssignment "
				+ "(Sprint 5A — BE-5A.3)")
public class TimeSlotController {

	private final TimeSlotService service;

	// =========================================================================
	// Assignment-scoped CRUD
	// =========================================================================

	@GetMapping("/teacher-assignments/{assignmentUuid}/time-slots")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List time slots of an assignment (TENANT_ADMIN)",
			description = "Sorted by (dayOfWeek asc, startTime asc). "
					+ "404 RESOURCE_NOT_FOUND if assignment UUID is unknown / cross-tenant."
	)
	public ResponseEntity<List<TimeSlotListItem>> list(
			@PathVariable UUID assignmentUuid
	) {
		return ResponseEntity.ok(service.listSlotsOfAssignment(assignmentUuid));
	}

	@PostMapping("/teacher-assignments/{assignmentUuid}/time-slots")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a time slot under an assignment (TENANT_ADMIN)",
			description = "400 TIME_SLOT_DATE_INVERTED if endTime <= startTime. "
					+ "409 TIME_SLOT_OVERLAP on overlap with another slot of the same "
					+ "assignment + day. 409 ASSIGNMENT_NOT_ACTIVE if the assignment "
					+ "has been soft-ended."
	)
	public ResponseEntity<ApiResponse<TimeSlotResponse>> create(
			@PathVariable UUID assignmentUuid,
			@Valid @RequestBody CreateTimeSlotRequest request
	) {
		TimeSlotResponse response = service.createSlot(assignmentUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	// =========================================================================
	// Flat slot routes
	// =========================================================================

	@GetMapping("/time-slots/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a time slot with its assignment ref (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<TimeSlotResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getSlot(publicUuid)));
	}

	@PutMapping("/time-slots/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a time slot (TENANT_ADMIN)",
			description = "Partial-merge. Cross-field rules (endTime > startTime, "
					+ "no overlap) re-evaluate against the post-merge state. "
					+ "teacher_assignment_id is intentionally NOT exposed; to move "
					+ "a slot between assignments, delete + recreate."
	)
	public ResponseEntity<ApiResponse<TimeSlotResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateTimeSlotRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateSlot(publicUuid, request)));
	}

	@DeleteMapping("/time-slots/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a time slot (TENANT_ADMIN)",
			description = "Time slots have no is_active flag (no \"paused\" state); "
					+ "deletion is the only retire path. To retire all slots of an "
					+ "assignment at once, soft-end the assignment instead."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteSlot(publicUuid);
		return ResponseEntity.noContent().build();
	}

	// =========================================================================
	// Reverse views
	// =========================================================================

	@GetMapping("/teachers/{teacherUuid}/schedule")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Teacher's weekly schedule (TENANT_ADMIN)",
			description = "Flat list of slots across every active assignment of the "
					+ "teacher; section + course + period populated per row. Optional "
					+ "?periodId narrows to a single period. Sorted by (dayOfWeek asc, "
					+ "startTime asc). 404 RESOURCE_NOT_FOUND if teacher / period UUID "
					+ "is unknown / cross-tenant."
	)
	public ResponseEntity<List<ScheduleSlotItem>> teacherSchedule(
			@PathVariable UUID teacherUuid,
			@Parameter(description = "Optional period filter")
			@RequestParam(name = "periodId", required = false) UUID periodId
	) {
		return ResponseEntity.ok(service.getTeacherSchedule(teacherUuid, periodId));
	}

	@GetMapping("/academic/sections/{sectionUuid}/schedule")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Section's weekly schedule (TENANT_ADMIN)",
			description = "Flat list of slots for every active assignment teaching the "
					+ "section; teacher + course + period populated per row. Optional "
					+ "?periodId narrows to a single period. Sorted by (dayOfWeek asc, "
					+ "startTime asc)."
	)
	public ResponseEntity<List<ScheduleSlotItem>> sectionSchedule(
			@PathVariable UUID sectionUuid,
			@Parameter(description = "Optional period filter")
			@RequestParam(name = "periodId", required = false) UUID periodId
	) {
		return ResponseEntity.ok(service.getSectionSchedule(sectionUuid, periodId));
	}
}
