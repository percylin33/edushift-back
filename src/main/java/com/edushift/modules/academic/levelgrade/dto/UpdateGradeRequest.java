package com.edushift.modules.academic.levelgrade.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code PUT /v1/academic/levels/{levelUuid}/grades/{gradeUuid}}.
 *
 * <p>Partial-merge: {@code null} fields are ignored. To <em>move</em>
 * a grade to a different level, change the {@code levelUuid} in the
 * URL by re-creating the grade — there is no field for that on purpose
 * (cross-level moves disturb downstream sections + enrollments and are
 * deferred to a future "move" endpoint if needed).</p>
 */
public record UpdateGradeRequest(

		@Size(min = 1, max = 100, message = "name length out of range")
		String name,

		@Positive(message = "ordinal must be >= 1")
		Integer ordinal
) {

	public boolean isEmpty() {
		return name == null && ordinal == null;
	}
}
