package com.edushift.modules.academic.section.service;

import com.edushift.modules.academic.section.dto.CreateSectionRequest;
import com.edushift.modules.academic.section.dto.SectionListItem;
import com.edushift.modules.academic.section.dto.SectionResponse;
import com.edushift.modules.academic.section.dto.UpdateSectionRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code Section} aggregate (Sprint 4 — BE-4.3).
 *
 * <h3>Filter semantics for {@link #listSections}</h3>
 * <ol>
 *   <li>If {@code academicYearPublicUuid} is provided, list scopes to that year.</li>
 *   <li>Otherwise, the currently {@code ACTIVE} year for the tenant is used.</li>
 *   <li>If no year is provided <em>and</em> there is no ACTIVE year (typical
 *       for a brand-new tenant before BE-4.1 activate), the response is
 *       an empty list — never an error.</li>
 *   <li>{@code gradePublicUuid} narrows the result to a single grade.</li>
 *   <li>{@code levelPublicUuid} narrows the result to all grades of a
 *       level. Mutually exclusive with {@code gradePublicUuid}: if both
 *       are provided, {@code gradePublicUuid} wins (stricter scope).</li>
 * </ol>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>section/year/grade/level publicUuid unknown for the tenant</td></tr>
 *   <tr><td>{@code SECTION_NAME_TAKEN}</td><td>409</td>
 *       <td>another section in the same {@code (year, grade)} already
 *       uses the same name (case-insensitive)</td></tr>
 *   <tr><td>{@code SECTION_HAS_ENROLLMENTS}</td><td>409</td>
 *       <td><em>Reserved for BE-4.8</em>: enrollments will activate this
 *       code on delete when present.</td></tr>
 *   <tr><td>{@code ACADEMIC_YEAR_LOCKED}</td><td>409</td>
 *       <td>the parent {@code AcademicYear} is in CLOSED status; create,
 *       update, delete are all rejected</td></tr>
 *   <tr><td>{@code SECTION_GRADE_LEVEL_MISMATCH}</td><td>409</td>
 *       <td>grade and level both supplied as filters but the grade does
 *       not belong to the level — caught for clean diagnostics in the FE</td></tr>
 * </table>
 */
public interface SectionService {

	List<SectionListItem> listSections(
			UUID academicYearPublicUuid,
			UUID gradePublicUuid,
			UUID levelPublicUuid);

	SectionResponse getSection(UUID publicUuid);

	SectionResponse createSection(CreateSectionRequest request);

	SectionResponse updateSection(UUID publicUuid, UpdateSectionRequest request);

	void deleteSection(UUID publicUuid);
}
