package com.edushift.modules.academic.competency.service;

import com.edushift.modules.academic.competency.dto.CapacityReorderRequest;
import com.edushift.modules.academic.competency.dto.CapacityResponse;
import com.edushift.modules.academic.competency.dto.CreateCapacityRequest;
import com.edushift.modules.academic.competency.dto.UpdateCapacityRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code Capacity} aggregate (Sprint 5A — BE-5A.2).
 *
 * <h3>Filters for {@link #listCapacities}</h3>
 * <ul>
 *   <li>{@code competencyUuid} — REQUIRED.</li>
 *   <li>{@code isActive} — only active or only inactive ({@code null} = both).</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>competency or capacity publicUuid unknown for the tenant</td></tr>
 *   <tr><td>{@code CAPACITY_CODE_TAKEN}</td><td>409</td>
 *       <td>another capacity in the same competency already uses
 *       {@code code} (case-insensitive)</td></tr>
 *   <tr><td>{@code CAPACITY_ORDER_TAKEN}</td><td>409</td>
 *       <td>insert/update tripped the partial unique index on
 *       {@code (competency_id, display_order)}</td></tr>
 *   <tr><td>{@code CAPACITY_OUT_OF_COMPETENCY}</td><td>409</td>
 *       <td>reorder payload references a capacity from another competency</td></tr>
 *   <tr><td>{@code CAPACITY_REORDER_INVALID}</td><td>409</td>
 *       <td>reorder payload has duplicate ordinals or duplicate UUIDs</td></tr>
 *   <tr><td>{@code CAPACITY_IN_USE_BY_SESSIONS}</td><td>409</td>
 *       <td>delete attempted while learning sessions reference the
 *       capacity (placeholder until BE-5A.4)</td></tr>
 * </table>
 */
public interface CapacityService {

	List<CapacityResponse> listCapacities(UUID competencyUuid, Boolean isActive);

	CapacityResponse getCapacity(UUID publicUuid);

	CapacityResponse createCapacity(UUID competencyUuid, CreateCapacityRequest request);

	CapacityResponse updateCapacity(UUID publicUuid, UpdateCapacityRequest request);

	List<CapacityResponse> reorderCapacities(UUID competencyUuid,
			CapacityReorderRequest request);

	void deleteCapacity(UUID publicUuid);
}
