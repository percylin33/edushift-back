package com.edushift.modules.academic.unit.service;

import com.edushift.modules.academic.unit.dto.SeedUnitsResponse;
import java.util.UUID;

/**
 * On-demand seed of standard units ({@code Unidad 1} … {@code Unidad N})
 * into a course. Idempotent when the course already has units.
 */
public interface UnitSeedService {

	/**
	 * @param courseUuid public UUID of the target course
	 * @param countOrNull requested count, or {@code null} to use the tenant default
	 * @param saveAsTenantDefault when true, persist the effective count as
	 *        {@code settings.academic.defaultUnitCount}
	 */
	SeedUnitsResponse seedForCourse(
			UUID courseUuid,
			Integer countOrNull,
			boolean saveAsTenantDefault);
}
