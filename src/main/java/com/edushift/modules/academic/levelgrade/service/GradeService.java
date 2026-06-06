package com.edushift.modules.academic.levelgrade.service;

import com.edushift.modules.academic.levelgrade.dto.CreateGradeRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeReorderRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeResponse;
import com.edushift.modules.academic.levelgrade.dto.UpdateGradeRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code Grade} aggregate (Sprint 4 — BE-4.2).
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>level or grade publicUuid unknown for the tenant</td></tr>
 *   <tr><td>{@code GRADE_ORDINAL_TAKEN}</td><td>409</td>
 *       <td>another grade in the same level already uses {@code ordinal}</td></tr>
 *   <tr><td>{@code GRADE_HAS_SECTIONS}</td><td>409</td>
 *       <td><em>Reserved for BE-4.3</em>: section-grade reference will
 *       activate this code when present.</td></tr>
 *   <tr><td>{@code GRADE_REORDER_INVALID}</td><td>400</td>
 *       <td>reorder payload references a grade not belonging to the
 *       parent level, or contains duplicate ordinals.</td></tr>
 * </table>
 */
public interface GradeService {

	List<GradeResponse> listGrades(UUID levelPublicUuid);

	GradeResponse createGrade(UUID levelPublicUuid, CreateGradeRequest request);

	GradeResponse updateGrade(UUID levelPublicUuid, UUID gradePublicUuid,
			UpdateGradeRequest request);

	void deleteGrade(UUID levelPublicUuid, UUID gradePublicUuid);

	List<GradeResponse> reorderGrades(UUID levelPublicUuid, GradeReorderRequest request);
}
