package com.edushift.modules.academic.section.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code PUT /v1/academic/sections/{publicUuid}}.
 *
 * <p>Partial-merge: {@code null} fields are ignored. To <em>move</em>
 * a section to a different (year, grade) tuple delete and re-create —
 * cross-tuple moves disrupt enrollments (BE-4.8) and are deliberately
 * not exposed.</p>
 *
 * <p>{@code capacity} accepts a sentinel-style payload: passing
 * {@code 0} via JSON is rejected by validation; to clear the cap the
 * client must omit the field — which under partial-merge means "no
 * change". A future PATCH endpoint with proper null-vs-absent
 * semantics is tracked as DEBT-API-1 (P3).</p>
 */
public record UpdateSectionRequest(

		@Size(min = 1, max = 40, message = "name length out of range")
		String name,

		@Positive(message = "capacity must be >= 1")
		Integer capacity,

		@Positive(message = "displayOrder must be >= 1")
		Integer displayOrder
) {

	public boolean isEmpty() {
		return name == null && capacity == null && displayOrder == null;
	}
}
