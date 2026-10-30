package com.edushift.modules.academic.competency.service;

import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.CompetencyReorderRequest;
import com.edushift.modules.academic.competency.dto.CompetencyResponse;
import com.edushift.modules.academic.competency.dto.CreateCompetencyRequest;
import com.edushift.modules.academic.competency.dto.UpdateCompetencyRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code Competency} aggregate (Sprint 5A — BE-5A.2).
 *
 * <h3>Filters for {@link #listCompetencies}</h3>
 * <ul>
 *   <li>{@code courseUuid} — REQUIRED.</li>
 *   <li>{@code isActive} — only active or only inactive ({@code null} = both).</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>course or competency publicUuid unknown for the tenant</td></tr>
 *   <tr><td>{@code COMPETENCY_CODE_TAKEN}</td><td>409</td>
 *       <td>another competency in the same course already uses {@code code}
 *       (case-insensitive)</td></tr>
 *   <tr><td>{@code COMPETENCY_ORDER_TAKEN}</td><td>409</td>
 *       <td>insert/update tripped the partial unique index on
 *       {@code (course_id, display_order)}</td></tr>
 *   <tr><td>{@code COMPETENCY_OUT_OF_COURSE}</td><td>409</td>
 *       <td>reorder payload references a competency from another course</td></tr>
 *   <tr><td>{@code COMPETENCY_REORDER_INVALID}</td><td>409</td>
 *       <td>reorder payload has duplicate ordinals or duplicate UUIDs</td></tr>
 *   <tr><td>{@code COMPETENCY_IN_USE_BY_SESSIONS}</td><td>409</td>
 *       <td>delete attempted while learning sessions reference the
 *       competency (placeholder until BE-5A.4)</td></tr>
 *   <tr><td>{@code TEACHER_NOT_ASSIGNED_TO_COURSE}</td><td>403</td>
 *       <td>teacher write without an active assignment on the course</td></tr>
 *   <tr><td>{@code COMPETENCY_ORIGIN_LOCKED}</td><td>403 or 409</td>
 *       <td>teacher touched a competency they do not own, or promote
 *       targeted a non-TEACHER origin</td></tr>
 *   <tr><td>{@code COMPETENCY_USED_BY_OTHER_TEACHER}</td><td>409</td>
 *       <td>teacher tried to deactivate a competency already used in
 *       another teacher's session</td></tr>
 *   <tr><td>{@code COMPETENCY_NEEDS_CAPACITY}</td><td>400</td>
 *       <td>teacher create without at least one capacity</td></tr>
 * </table>
 */
public interface CompetencyService {

	List<CompetencyListItem> listCompetencies(UUID courseUuid, Boolean isActive);

	CompetencyResponse getCompetency(UUID publicUuid);

	CompetencyResponse createCompetency(UUID courseUuid, CreateCompetencyRequest request);

	CompetencyResponse updateCompetency(UUID publicUuid, UpdateCompetencyRequest request);

	List<CompetencyResponse> reorderCompetencies(UUID courseUuid,
			CompetencyReorderRequest request);

	void deleteCompetency(UUID publicUuid);

	/**
	 * Sets {@code isActive=false}. A teacher may only deactivate a
	 * {@code TEACHER} competency they created, and only when no other
	 * teacher's session references it. An admin may deactivate any origin.
	 */
	CompetencyResponse deactivateCompetency(UUID publicUuid);

	/**
	 * Promotes a {@code TEACHER} competency to {@code INSTITUTIONAL}.
	 * Admin only. {@code created_by} is left unchanged.
	 */
	CompetencyResponse promoteCompetency(UUID publicUuid);
}
