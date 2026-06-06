package com.edushift.modules.academic.year.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload of {@code POST /v1/academic/years}.
 *
 * <p>The status is always set to {@code PLANNING} on creation; activation
 * is a dedicated endpoint ({@code POST /years/{publicUuid}/activate}).</p>
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>{@code name}: required, 1..50 chars. Trimmed by the entity.</li>
 *   <li>{@code startDate} / {@code endDate}: required.
 *       Cross-field rule {@code startDate < endDate} is enforced by the
 *       service (and as a CHECK in the DB).</li>
 * </ul>
 */
public record CreateAcademicYearRequest(

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 50, message = "name length out of range")
		String name,

		@NotNull(message = "startDate is required")
		LocalDate startDate,

		@NotNull(message = "endDate is required")
		LocalDate endDate
) {
}
