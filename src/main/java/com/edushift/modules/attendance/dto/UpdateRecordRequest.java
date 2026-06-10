package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/attendance/records/{id}}
 * (Sprint 6 / BE-6.2.D).
 *
 * <p>Both fields are optional individually but at least one must be
 * present (enforced via {@link #isEmpty()} in the service to surface
 * a clear {@code RECORD_EMPTY_PATCH} message).
 *
 * <h3>Authorization</h3>
 * The 24h post-close window is enforced by the service and depends on
 * the caller's role:
 * <ul>
 *   <li>{@code TEACHER} — only allowed within
 *       {@code now - session.closedAt <= 24h}.</li>
 *   <li>{@code TENANT_ADMIN} — no window.</li>
 * </ul>
 */
public record UpdateRecordRequest(
		AttendanceRecordStatus status,
		@Size(max = 500) String notes
) {

	/**
	 * @return {@code true} when neither field is set.
	 */
	public boolean isEmpty() {
		return status == null && (notes == null);
	}
}
