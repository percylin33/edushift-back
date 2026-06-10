package com.edushift.modules.evaluations.evaluationrubric.dto;

import com.edushift.shared.validation.annotations.ValidUuid;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /v1/academic/evaluations/{publicUuid}/rubric}
 * (Sprint 5B / BE-5B.4).
 *
 * <p>Carries the rubric the caller wants to attach. If the evaluation
 * already has another rubric attached, the existing link is
 * soft-deleted and the new one is inserted in the same transaction
 * (replace semantics — see {@code EvaluationRubricService.attachRubric}
 * for the contract).</p>
 *
 * @param rubricPublicUuid public UUID of the {@code Rubric} to attach.
 *                         Required. The rubric must belong to the
 *                         active tenant; cross-tenant attachments
 *                         collapse to {@code 404 RESOURCE_NOT_FOUND}.
 */
public record AttachRubricRequest(

        @NotBlank(message = "rubricPublicUuid is required")
        @ValidUuid(message = "rubricPublicUuid must be a valid UUID")
        String rubricPublicUuid

) {
}
