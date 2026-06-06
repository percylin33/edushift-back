package com.edushift.modules.academic.levelgrade.service;

import com.edushift.modules.academic.levelgrade.dto.AcademicLevelResponse;
import com.edushift.modules.academic.levelgrade.dto.CreateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.dto.UpdateAcademicLevelRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code AcademicLevel} aggregate (Sprint 4 — BE-4.2).
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>publicUuid unknown for the current tenant</td></tr>
 *   <tr><td>{@code LEVEL_CODE_TAKEN}</td><td>409</td>
 *       <td>another level in the tenant already uses {@code code}</td></tr>
 *   <tr><td>{@code LEVEL_HAS_GRADES}</td><td>409</td>
 *       <td>delete attempted on a level that still has grades</td></tr>
 *   <tr><td>{@code LEVEL_IN_USE_BY_COURSES}</td><td>409</td>
 *       <td><em>Reserved for BE-4.4</em>: course-level pivot will activate
 *       this code when present.</td></tr>
 * </table>
 */
public interface AcademicLevelService {

	List<AcademicLevelResponse> listLevels();

	AcademicLevelResponse getLevel(UUID publicUuid);

	AcademicLevelResponse createLevel(CreateAcademicLevelRequest request);

	AcademicLevelResponse updateLevel(UUID publicUuid, UpdateAcademicLevelRequest request);

	void deleteLevel(UUID publicUuid);
}
