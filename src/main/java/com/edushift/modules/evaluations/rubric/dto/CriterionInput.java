package com.edushift.modules.evaluations.rubric.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Input for a single weighted criterion in a {@code Rubric}.
 *
 * <p>Used by both {@code CreateRubricRequest} and
 * {@code UpdateRubricRequest}. The shape is mirrored on the entity as
 * a {@code Map<String, Object>} in the {@code criteria} JSONB column;
 * this record is the typed contract at the API boundary.</p>
 *
 * <h3>Validation summary</h3>
 * <ul>
 *   <li>{@code key}: snake_case slug, used as a stable identifier for
 *       snapshotting grade records. Must be unique within a rubric.</li>
 *   <li>{@code weight}: in [0, 100]. The service layer enforces the
 *       sum of all weights = 100.0 (RUB_CRITERIA_WEIGHT_SUM).</li>
 *   <li>{@code descriptors}: 0..4 items, one per achievement level.
 *       The {@code level} code must match a level in the rubric's
 *       {@code levels[]} array (validated by the service).</li>
 * </ul>
 */
public record CriterionInput(
		@NotBlank
		@Size(max = 64)
		@Pattern(regexp = "^[a-z0-9_]+$",
				message = "must be snake_case (a-z, 0-9, underscore)")
		String key,

		@NotBlank
		@Size(max = 160)
		String name,

		@Size(max = 1000)
		String description,

		@NotNull
		@DecimalMin("0.00")
		@DecimalMax("100.00")
		BigDecimal weight,

		@Size(max = 4)
		List<@NotNull DescriptorInput> descriptors
) {
}
