package com.edushift.modules.academic.unit.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code PATCH /v1/academic/courses/{courseUuid}/units/reorder}.
 *
 * <p>The full set of units belonging to the course (or any subset) is
 * sent with the new ordinals. The service applies the change in a
 * single transaction with a two-pass strategy that mirrors
 * {@code GradeReorderRequest} (BE-4.2): write tmp negative ordinals
 * first, then bump to the target value to avoid tripping the partial
 * unique index {@code uk_academic_units_course_order_active}.</p>
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>{@code items} must not be empty (no-op operations rejected with 400).</li>
 *   <li>Each item must carry both {@code publicUuid} and a positive
 *       {@code displayOrder}.</li>
 *   <li>Any {@code publicUuid} not belonging to the course is rejected
 *       with {@code UNIT_OUT_OF_COURSE} (400).</li>
 *   <li>Duplicate ordinals across items are rejected with
 *       {@code UNIT_REORDER_INVALID} (400).</li>
 * </ul>
 */
public record UnitReorderRequest(

		@NotEmpty(message = "items must not be empty")
		@Valid
		List<Item> items
) {

	public record Item(
			@NotNull(message = "publicUuid is required") UUID publicUuid,
			@NotNull(message = "displayOrder is required")
			@Positive(message = "displayOrder must be >= 1") Integer displayOrder
	) {
	}
}
