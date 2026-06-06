package com.edushift.modules.academic.period.service;

import com.edushift.modules.academic.period.dto.AcademicPeriodListItem;
import com.edushift.modules.academic.period.dto.AcademicPeriodResponse;
import com.edushift.modules.academic.period.dto.CreateAcademicPeriodRequest;
import com.edushift.modules.academic.period.dto.UpdateAcademicPeriodRequest;
import com.edushift.modules.academic.period.entity.PeriodType;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code AcademicPeriod} aggregate
 * (Sprint 4 — BE-4.5).
 *
 * <h3>Filters for {@link #listPeriods}</h3>
 * <ul>
 *   <li>{@code academicYearPublicUuid} — required key. If null, the
 *       service falls back to the active year of the tenant. If the
 *       tenant has no active year, the result is an empty list.</li>
 *   <li>{@code periodType} — optional narrow.</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>period or year publicUuid unknown for the tenant</td></tr>
 *   <tr><td>{@code ACADEMIC_YEAR_LOCKED}</td><td>409</td>
 *       <td>year is {@code CLOSED} — only reads allowed</td></tr>
 *   <tr><td>{@code PERIOD_OUT_OF_YEAR_RANGE}</td><td>409</td>
 *       <td>{@code [startDate, endDate]} not contained in
 *           {@code [year.startDate, year.endDate]}</td></tr>
 *   <tr><td>{@code PERIOD_ORDINAL_GAP}</td><td>400</td>
 *       <td>create with ordinal that leaves a gap (must be
 *           {@code max(existing)+1} for {@code (year,type)})</td></tr>
 *   <tr><td>{@code PERIOD_ORDINAL_TAKEN}</td><td>409</td>
 *       <td>create with ordinal already used in
 *           {@code (year,type)}</td></tr>
 *   <tr><td>{@code PERIOD_DATE_OVERLAP}</td><td>409</td>
 *       <td>range overlaps another period of same {@code (year,type)}</td></tr>
 *   <tr><td>{@code PERIOD_DATE_INVERTED}</td><td>400</td>
 *       <td>endDate &lt; startDate</td></tr>
 *   <tr><td>{@code PERIOD_NOT_LAST_ORDINAL}</td><td>409</td>
 *       <td>delete not allowed on a middle ordinal — only the highest
 *           ordinal can be deleted to keep contiguity</td></tr>
 *   <tr><td>{@code PERIOD_IN_USE_BY_ASSIGNMENTS}</td><td>409</td>
 *       <td><em>Reserved for BE-4.7</em></td></tr>
 * </table>
 */
public interface AcademicPeriodService {

	List<AcademicPeriodListItem> listPeriods(UUID academicYearPublicUuid, PeriodType periodType);

	AcademicPeriodResponse getPeriod(UUID publicUuid);

	AcademicPeriodResponse createPeriod(CreateAcademicPeriodRequest request);

	AcademicPeriodResponse updatePeriod(UUID publicUuid, UpdateAcademicPeriodRequest request);

	void deletePeriod(UUID publicUuid);
}
