package com.edushift.modules.academic.year.service;

import com.edushift.modules.academic.year.dto.AcademicYearListItem;
import com.edushift.modules.academic.year.dto.AcademicYearResponse;
import com.edushift.modules.academic.year.dto.CreateAcademicYearRequest;
import com.edushift.modules.academic.year.dto.UpdateAcademicYearRequest;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the academic-year aggregate (Sprint 4 — BE-4.1).
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>{@code publicUuid} unknown for the current tenant</td></tr>
 *   <tr><td>{@code ACADEMIC_YEAR_NAME_TAKEN}</td><td>409</td>
 *       <td>another year in the tenant already uses {@code name}
 *       (case-insensitive)</td></tr>
 *   <tr><td>{@code ACADEMIC_YEAR_INVALID_DATE_RANGE}</td><td>400</td>
 *       <td>{@code startDate >= endDate}</td></tr>
 *   <tr><td>{@code ACADEMIC_YEAR_LOCKED}</td><td>409</td>
 *       <td>attempted mutation on a CLOSED year</td></tr>
 *   <tr><td>{@code ACADEMIC_YEAR_NOT_ACTIVATABLE}</td><td>409</td>
 *       <td>activate called on CLOSED year</td></tr>
 * </table>
 */
public interface AcademicYearService {

	List<AcademicYearListItem> listYears(AcademicYearStatus statusFilter);

	AcademicYearResponse getYear(UUID publicUuid);

	AcademicYearResponse createYear(CreateAcademicYearRequest request);

	AcademicYearResponse updateYear(UUID publicUuid, UpdateAcademicYearRequest request);

	AcademicYearResponse activateYear(UUID publicUuid);

	void deleteYear(UUID publicUuid);
}
