package com.edushift.modules.academic.unit.service;

import com.edushift.modules.academic.unit.dto.CreateUnitRequest;
import com.edushift.modules.academic.unit.dto.UnitListItem;
import com.edushift.modules.academic.unit.dto.UnitReorderRequest;
import com.edushift.modules.academic.unit.dto.UnitResponse;
import com.edushift.modules.academic.unit.dto.UpdateUnitRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code Unit} aggregate (Sprint 5A — BE-5A.1).
 *
 * <h3>Filters for {@link #listUnits}</h3>
 * <ul>
 *   <li>{@code courseUuid} — REQUIRED; units always live under a course.</li>
 *   <li>{@code isActive} — only active or only inactive ({@code null} = both).</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>course or unit publicUuid unknown for the tenant (incl. cross-tenant)</td></tr>
 *   <tr><td>{@code UNIT_NAME_EXISTS}</td><td>409</td>
 *       <td>another unit in the same course already uses {@code name}
 *       (case-insensitive)</td></tr>
 *   <tr><td>{@code UNIT_DATE_INVERTED}</td><td>400</td>
 *       <td>{@code endDate < startDate}</td></tr>
 *   <tr><td>{@code UNIT_HAS_SESSIONS}</td><td>409</td>
 *       <td>delete attempted while learning sessions reference the unit
 *       (wired up in BE-5A.4 — placeholder until then)</td></tr>
 *   <tr><td>{@code UNIT_REORDER_INVALID}</td><td>409</td>
 *       <td>reorder payload has duplicate ordinals or duplicate UUIDs</td></tr>
 *   <tr><td>{@code UNIT_OUT_OF_COURSE}</td><td>409</td>
 *       <td>reorder payload references a unit that belongs to another course</td></tr>
 *   <tr><td>{@code UNIT_ORDER_TAKEN}</td><td>409</td>
 *       <td>final ordinal collides with a unit not included in the reorder
 *       payload</td></tr>
 * </table>
 */
public interface UnitService {

	List<UnitListItem> listUnits(UUID courseUuid, Boolean isActive);

	UnitResponse getUnit(UUID publicUuid);

	UnitResponse createUnit(UUID courseUuid, CreateUnitRequest request);

	UnitResponse updateUnit(UUID publicUuid, UpdateUnitRequest request);

	List<UnitResponse> reorderUnits(UUID courseUuid, UnitReorderRequest request);

	void deleteUnit(UUID publicUuid);
}
