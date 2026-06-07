package com.edushift.modules.academic.competency.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code PATCH /v1/academic/competencies/{competencyUuid}/capacities/reorder}.
 *
 * <p>Same two-pass strategy as the rest of the academic reorder
 * endpoints (grades / units / competencies).</p>
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>{@code items} must not be empty.</li>
 *   <li>Each item must carry {@code publicUuid} + positive
 *       {@code displayOrder}.</li>
 *   <li>Any UUID not belonging to the competency → {@code CAPACITY_OUT_OF_COMPETENCY}
 *       (409).</li>
 *   <li>Duplicate ordinals or duplicate UUIDs → {@code CAPACITY_REORDER_INVALID}
 *       (409).</li>
 * </ul>
 */
public record CapacityReorderRequest(

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
