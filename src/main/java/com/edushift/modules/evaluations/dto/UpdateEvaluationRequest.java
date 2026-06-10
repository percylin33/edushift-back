package com.edushift.modules.evaluations.dto;

import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.shared.validation.annotations.ValidUuid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload of {@code PUT /v1/academic/evaluations/{publicUuid}}.
 *
 * <p>Partial-merge: {@code null} fields are ignored. Lifecycle
 * transitions go through the dedicated endpoints
 * ({@code POST /publish} and {@code POST /close}) and are not
 * addressable from here.</p>
 *
 * <h3>Editability matrix</h3>
 * <ul>
 *   <li>{@code DRAFT} — all fields editable.</li>
 *   <li>{@code PUBLISHED} — only {@code description} and {@code dueDate}
 *       are editable. The service returns {@code EVAL_NOT_EDITABLE} (409)
 *       on any other change.</li>
 *   <li>{@code CLOSED} — no fields editable, request is rejected with
 *       {@code EVAL_CLOSED} (409).</li>
 * </ul>
 */
public record UpdateEvaluationRequest(

		EvaluationKind kind,

		@Size(min = 1, max = 200, message = "name length out of range")
		String name,

		@Size(max = 4000, message = "description too long")
		String description,

		@DecimalMin(value = "0.00", message = "weight must be >= 0.00")
		@DecimalMax(value = "999.99", message = "weight must be <= 999.99")
		BigDecimal weight,

		LocalDate scheduledDate,

		LocalDate dueDate,

		EvaluationScale scale,

		@ValidUuid(message = "unitPublicUuid must be a valid UUID")
		String unitPublicUuid,

		@ValidUuid(message = "learningSessionPublicUuid must be a valid UUID")
		String learningSessionPublicUuid,

		Boolean isActive
) {

	public boolean isEmpty() {
		return kind == null
				&& name == null
				&& description == null
				&& weight == null
				&& scheduledDate == null
				&& dueDate == null
				&& scale == null
				&& unitPublicUuid == null
				&& learningSessionPublicUuid == null
				&& isActive == null;
	}
}
