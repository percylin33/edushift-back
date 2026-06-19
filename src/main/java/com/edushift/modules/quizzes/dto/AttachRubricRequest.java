package com.edushift.modules.quizzes.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * JSON body for {@code PATCH /v1/lms/quizzes/{uuid}/rubric}
 * (Sprint 7b / BE-7b.3).
 *
 * <p>{@code rubricPublicUuid} is the rubric the teacher wants to
 * attach to the quiz. Sending {@code null} detaches the current
 * rubric (clears {@code lms_quizzes.rubric_id} and
 * {@code rubric_evaluation_id}, and soft-deletes the derived
 * evaluation if it has no grades yet).
 *
 * <p>The attached rubric is enforced to belong to the same tenant
 * as the quiz; cross-tenant lookups resolve as 404
 * (anti-enumeration).
 */
public record AttachRubricRequest(
		@NotNull UUID rubricPublicUuid
) {
}
