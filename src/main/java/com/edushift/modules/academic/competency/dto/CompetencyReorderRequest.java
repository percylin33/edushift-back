package com.edushift.modules.academic.competency.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code PATCH /v1/academic/courses/{courseUuid}/competencies/reorder}.
 *
 * <p>Same two-pass strategy as
 * {@link com.edushift.modules.academic.unit.dto.UnitReorderRequest} (BE-5A.1)
 * and {@code GradeReorderRequest} (BE-4.2): write tmp negative ordinals
 * first, then bump to the target value to avoid tripping the partial
 * unique index {@code uk_competencies_course_order_active}.</p>
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>{@code items} must not be empty (no-op rejected with 400).</li>
 *   <li>Each item must carry {@code publicUuid} + positive
 *       {@code displayOrder}.</li>
 *   <li>Any UUID not belonging to the course → {@code COMPETENCY_OUT_OF_COURSE}
 *       (409).</li>
 *   <li>Duplicate ordinals or duplicate UUIDs → {@code COMPETENCY_REORDER_INVALID}
 *       (409).</li>
 * </ul>
 */
public record CompetencyReorderRequest(

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
