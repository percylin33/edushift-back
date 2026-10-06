package com.edushift.modules.academic.unit.dto;

import java.util.List;

/**
 * Payload for {@code POST /v1/academic/courses/{courseUuid}/units/seed-defaults}.
 *
 * <p>{@code seeded=false} means the course already had at least one unit
 * (idempotent skip). {@code countRequested} is the N that was requested /
 * resolved from tenant settings.</p>
 */
public record SeedUnitsResponse(
		boolean seeded,
		int countRequested,
		int unitsCreated,
		List<UnitListItem> created
) {
}
