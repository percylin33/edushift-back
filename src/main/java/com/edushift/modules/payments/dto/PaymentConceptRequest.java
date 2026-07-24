package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.PaymentConcept;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body to create or update a {@link PaymentConcept}.
 *
 * <p>Validation notes:</p>
 * <ul>
 *   <li>{@code code}: uppercase alphanumeric + underscore (matches the
 *       {@code ^CODE$} regex), max 40 chars; matches the existing
 *       whitelist used elsewhere (e.g. {@code Course.code}).</li>
 *   <li>{@code defaultAmountCents}: ≥ 0; 0 means "amount decided at
 *       invoice emit time".</li>
 *   <li>{@code currency}: ISO-4217 alpha-3 uppercase.</li>
 * </ul>
 */
public record PaymentConceptRequest(
		@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String code,
		@NotBlank @Size(max = 120) String name,
		@Size(max = 500) String description,
		@NotNull PaymentConcept.Category category,
		@NotNull @Min(0) Long defaultAmountCents,
		@NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
		Boolean recurring,
		Boolean active,
		Integer sortOrder
) {
}