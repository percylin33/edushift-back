package com.edushift.modules.evaluations.rubric.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for {@code PATCH /v1/academic/rubrics/{uuid}}.
 *
 * <p>Partial-merge: only non-null fields are applied. Sending an empty
 * body is a no-op (HTTP 200 + unchanged entity). System rubrics
 * ({@code isSystem = true}) cannot be patched — the service returns
 * {@code RUB_SYSTEM_READ_ONLY} (403) and the controller short-circuits
 * before reaching this DTO.</p>
 *
 * <p>Replacing {@code criteria} or {@code levels} requires passing the
 * full new list (the service does a full overwrite, not a diff).
 * Validation of the resulting shape (1..10 criteria, 2..4 levels,
 * weight sum 100.0) is identical to {@link CreateRubricRequest}.</p>
 */
public record UpdateRubricRequest(
		@Size(max = 160)
		String name,

		@Size(max = 2000)
		String description,

		@Size(max = 10)
		@Valid
		List<CriterionInput> criteria,

		@Size(min = 2, max = 4)
		@Valid
		List<LevelInput> levels
) {

	/** True iff every field is null. Used by the service to short-circuit. */
	public boolean isEmpty() {
		return name == null
				&& description == null
				&& criteria == null
				&& levels == null;
	}
}
