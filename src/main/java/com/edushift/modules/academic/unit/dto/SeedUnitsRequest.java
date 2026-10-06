package com.edushift.modules.academic.unit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Optional body for {@code POST .../units/seed-defaults}.
 *
 * <p>{@code count} null → use tenant {@code settings.academic.defaultUnitCount}
 * (or system default 8). When {@code saveAsTenantDefault} is true, the
 * effective count is written back to the tenant setting.</p>
 */
public record SeedUnitsRequest(
		@Min(1) @Max(20) Integer count,
		Boolean saveAsTenantDefault
) {
}
