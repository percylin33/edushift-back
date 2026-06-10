package com.edushift.modules.evaluations.dto;

import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.shared.validation.annotations.ValidUuid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload of {@code POST /v1/academic/assignments/{assignmentUuid}/evaluations}.
 *
 * <p>Validation rules mirrored at the service layer:</p>
 * <ul>
 *   <li>{@code name} is unique inside the assignment case-insensitively
 *       ({@code EVAL_NAME_EXISTS}, 409).</li>
 *   <li>{@code kind} and {@code scale} must be coherent — see
 *       {@code EvaluationServiceImpl.assertKindScaleCoherent}
 *       ({@code EVAL_KIND_SCALE_MISMATCH}, 400).</li>
 *   <li>{@code dueDate >= scheduledDate} when both are present
 *       ({@code EVAL_DATE_INVERTED}, 400).</li>
 *   <li>If {@code unitPublicUuid} is present, the unit must belong to
 *       the same course as the assignment ({@code EVAL_UNIT_NOT_IN_COURSE}, 400).</li>
 *   <li>If {@code learningSessionPublicUuid} is present, the session
 *       must belong to the same assignment ({@code EVAL_SESSION_NOT_IN_ASSIGNMENT}, 400).</li>
 *   <li>Initial status is always {@code DRAFT} — caller cannot
 *       pre-set {@code PUBLISHED} on create.</li>
 * </ul>
 */
public record CreateEvaluationRequest(

		@NotNull(message = "kind is required")
		EvaluationKind kind,

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 200, message = "name length out of range")
		String name,

		@Size(max = 4000, message = "description too long")
		String description,

		@NotNull(message = "weight is required")
		@DecimalMin(value = "0.00", message = "weight must be >= 0.00")
		@DecimalMax(value = "999.99", message = "weight must be <= 999.99")
		BigDecimal weight,

		@NotNull(message = "scheduledDate is required")
		@PastOrPresent(message = "scheduledDate cannot be in the future")
		LocalDate scheduledDate,

		LocalDate dueDate,

		@NotNull(message = "scale is required")
		EvaluationScale scale,

		@ValidUuid(message = "unitPublicUuid must be a valid UUID")
		String unitPublicUuid,

		@ValidUuid(message = "learningSessionPublicUuid must be a valid UUID")
		String learningSessionPublicUuid,

		Boolean isActive
) {
}
