package com.edushift.modules.evaluations.rubric.service;

import com.edushift.modules.evaluations.rubric.dto.CreateRubricRequest;
import com.edushift.modules.evaluations.rubric.dto.RubricFilters;
import com.edushift.modules.evaluations.rubric.dto.RubricListItem;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.rubric.dto.UpdateRubricRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code Rubric} aggregate (Sprint 5B / BE-5B.2).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@link #listRubrics} — tenant-wide listing with filters
 *       ({@code systemOnly}, {@code isActive}, free-text query).</li>
 *   <li>{@link #listSystemRubrics} — convenience for the system
 *       library; on first call, materialises the MINEDU seed
 *       (ADR-5B.10).</li>
 *   <li>{@link #getRubric} — full detail.</li>
 *   <li>{@link #createRubric} — tenant-owned rubric from scratch.</li>
 *   <li>{@link #forkRubric} — clone a system rubric to a tenant-owned
 *       one.</li>
 *   <li>{@link #updateRubric} — partial-merge; system rubrics are
 *       read-only (use {@code forkRubric} instead).</li>
 *   <li>{@link #deleteRubric} — soft-delete; system rubrics are
 *       protected.</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>rubric publicUuid unknown for the tenant (incl. cross-tenant)</td></tr>
 *   <tr><td>{@code RUB_NAME_EXISTS}</td><td>409</td>
 *       <td>another rubric in the same tenant already uses {@code name}
 *           (case-insensitive)</td></tr>
 *   <tr><td>{@code RUB_SYSTEM_READ_ONLY}</td><td>403</td>
 *       <td>try to mutate a system (MINEDU-seed) rubric</td></tr>
 *   <tr><td>{@code RUB_CRITERIA_WEIGHT_SUM}</td><td>400</td>
 *       <td>sum of criterion weights is not 100.0</td></tr>
 *   <tr><td>{@code RUB_CRITERIA_COUNT}</td><td>400</td>
 *       <td>number of criteria is not in 1..10</td></tr>
 *   <tr><td>{@code RUB_LEVELS_COUNT}</td><td>400</td>
 *       <td>number of levels is not in 2..4</td></tr>
 *   <tr><td>{@code RUB_LEVEL_CODE_DUPLICATE}</td><td>400</td>
 *       <td>two levels share the same code</td></tr>
 *   <tr><td>{@code RUB_LEVEL_UNKNOWN}</td><td>400</td>
 *       <td>descriptor references a level code not in
 *           {@code levels[]}</td></tr>
 *   <tr><td>{@code RUB_CRITERION_KEY_DUPLICATE}</td><td>400</td>
 *       <td>two criteria share the same key</td></tr>
 *   <tr><td>{@code RUB_DESCRIPTOR_DUPLICATE}</td><td>400</td>
 *       <td>two descriptors on the same criterion target the same level</td></tr>
 *   <tr><td>{@code RUB_CANNOT_FORK_NON_SYSTEM}</td><td>400</td>
 *       <td>fork attempted on a tenant-owned (non-system) rubric</td></tr>
 *   <tr><td>{@code RUB_PARENT_NOT_FOUND}</td><td>404</td>
 *       <td>{@code parentRubricId} references a non-existent rubric</td></tr>
 * </table>
 */
public interface RubricService {

	List<RubricListItem> listRubrics(RubricFilters filters);

	/**
	 * Returns the system-rubric library, materialising the MINEDU seed
	 * on the first call per tenant. Idempotent.
	 */
	List<RubricListItem> listSystemRubrics();

	RubricResponse getRubric(UUID publicUuid);

	RubricResponse createRubric(CreateRubricRequest request);

	/**
	 * Forks a system rubric into a tenant-owned one. The caller can
	 * override {@code name}, {@code description}, {@code criteria} and
	 * {@code levels} via {@code request} (all optional); missing fields
	 * default to a copy of the source with a "{tenant-name} fork" suffix
	 * on the name.
	 */
	RubricResponse forkRubric(UUID sourcePublicUuid, CreateRubricRequest request);

	RubricResponse updateRubric(UUID publicUuid, UpdateRubricRequest request);

	void deleteRubric(UUID publicUuid);
}
