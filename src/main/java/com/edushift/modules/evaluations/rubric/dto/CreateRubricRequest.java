package com.edushift.modules.evaluations.rubric.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for {@code POST /v1/academic/rubrics} and
 * {@code POST /v1/academic/rubrics/{uuid}/fork}.
 *
 * <p>Forks reuse this DTO: the service layer copies {@code name}
 * (with a "{tenant} fork" suffix by default) and sets
 * {@code parentRubricId} from the URL. The caller can override
 * {@code name}, {@code description}, {@code criteria} and
 * {@code levels} on fork — the system rubric itself is never mutated.</p>
 *
 * <h3>Validation chain (all enforced server-side)</h3>
 * <ol>
 *   <li>Bean Validation (jakarta) — fields are present and shaped.</li>
 *   <li>Service layer — {@code criteria} count in 1..10, levels count
 *       in 2..4, weights sum to 100.0, level codes unique, descriptor
 *       levels refer to a defined level.</li>
 *   <li>Database — unique index on {@code (tenant, lower(name))} catches
 *       case-insensitive collisions and concurrent races.</li>
 * </ol>
 */
public record CreateRubricRequest(
		@NotBlank
		@Size(max = 160)
		String name,

		@Size(max = 2000)
		String description,

		@NotEmpty
		@Size(max = 10)
		@Valid
		List<CriterionInput> criteria,

		@NotEmpty
		@Size(min = 2, max = 4)
		@Valid
		List<LevelInput> levels
) {
}
