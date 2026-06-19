package com.edushift.modules.quizzes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * JSON body for {@code POST /questions/{uuid}/options}
 * (Sprint 7b / BE-7b.1). Adds a single option to an existing
 * MC question while the quiz is in DRAFT.
 *
 * <p>After the last option is added the service re-validates the
 * "exactly one correct" invariant on the full option set.
 */
public record AddOptionRequest(
		@NotNull @Valid CreateOptionRequest option
) {
}
