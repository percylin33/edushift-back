package com.edushift.modules.evaluations.rubric.service;

import com.edushift.modules.evaluations.rubric.dto.RubricListItem;
import java.util.List;

/**
 * On-demand seed of the MINEDU baseline library of {@code Rubric}
 * templates into the current tenant (Sprint 5B / BE-5B.2,
 * ADR-5B.10).
 *
 * <p>Unlike {@code AcademicSeedService} (which runs during
 * self-signup), this seed is invoked implicitly by the controller when
 * the tenant hits {@code GET /academic/rubrics/system} and no system
 * rows exist yet. The behaviour is invisible to the caller: the first
 * read materialises the library; subsequent reads are a no-op.</p>
 *
 * <h3>Idempotency contract</h3>
 * <ul>
 *   <li>If the tenant already has at least one system row, the call
 *       is a no-op and returns the existing rows as the result.</li>
 *   <li>If the seed has been triggered previously and the catalog grew
 *       (e.g. Sprint N+1 adds a new template), the service inserts
 *       only the missing rows by name (idempotent re-run).</li>
 * </ul>
 *
 * <h3>Visibility</h3>
 * Called from the controller before the {@code findByPublicUuid} read,
 * so the endpoint effectively "warms up" the seed for the tenant.
 */
public interface RubricSeedService {

	/**
	 * Materialises the MINEDU seed library in the current tenant and
	 * returns the resulting system-rubric list. Idempotent.
	 */
	List<RubricListItem> materializeSystemRubrics();
}
