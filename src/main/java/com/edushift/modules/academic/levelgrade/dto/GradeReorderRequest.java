package com.edushift.modules.academic.levelgrade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code PATCH /v1/academic/levels/{levelUuid}/grades/reorder}.
 *
 * <p>The full set of grades belonging to the level (or any subset) is
 * sent with the new ordinals. The service applies the change in a
 * single transaction with a two-pass strategy that avoids tripping the
 * {@code uk_grades_level_ordinal_active} unique index midway through.</p>
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>{@code items} must not be empty (otherwise the operation is a no-op
 *       and we'd rather get a 400 than a silent 200).</li>
 *   <li>Each item must carry both {@code publicUuid} and a positive
 *       {@code ordinal}.</li>
 *   <li>Duplicate ordinals across items are rejected by the service with
 *       {@code GRADE_ORDINAL_TAKEN}.</li>
 * </ul>
 */
public record GradeReorderRequest(

		@NotEmpty(message = "items must not be empty")
		@Valid
		List<Item> items
) {

	public record Item(
			@NotNull(message = "publicUuid is required") UUID publicUuid,
			@NotNull(message = "ordinal is required")
			@Positive(message = "ordinal must be >= 1") Integer ordinal
	) {
	}
}
