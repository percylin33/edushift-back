package com.edushift.modules.schooldashboard.service;

import com.edushift.modules.schooldashboard.dto.SchoolOperationsDashboardSummary;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, tenant-scoped operational indicators for a private school. */
@Service
@RequiredArgsConstructor
public class SchoolOperationsDashboardService {
	private final CurrentUserProvider currentUserProvider;
	private final NamedParameterJdbcTemplate jdbc;

	@Transactional(readOnly = true)
	public SchoolOperationsDashboardSummary getSummary() {
		UUID tenantId = currentUserProvider.currentTenantId().orElseThrow(
				() -> new UnauthorizedException("Authenticated tenant is required for dashboard"));

		List<AcademicYearRef> activeYears = jdbc.query(
				"""
				select id, name
				from edushift.academic_years
				where tenant_id = :tenantId and status = 'ACTIVE' and deleted = false
				limit 1
				""",
				new MapSqlParameterSource("tenantId", tenantId),
				(rs, rowNum) -> new AcademicYearRef(
						rs.getObject("id", UUID.class), rs.getString("name")));

		if (activeYears.isEmpty()) {
			return SchoolOperationsDashboardSummary.empty(Instant.now());
		}

		AcademicYearRef year = activeYears.getFirst();
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("tenantId", tenantId)
				.addValue("yearId", year.id());
		return jdbc.queryForObject(
				"""
				with disclosure_counts as (
				  select count(*) as total,
				         count(*) filter (where status <> 'PUBLISHED') as pending,
				         count(*) filter (where status = 'PUBLISHED') as published
				  from edushift.annual_disclosure_packages
				  where tenant_id = :tenantId and academic_year_id = :yearId and deleted = false
				), admission_counts as (
				  select count(*) filter (where status in ('RECEIVED', 'UNDER_REVIEW')) as in_process,
				         count(*) filter (where status = 'WAITLISTED') as waitlisted,
				         count(*) filter (where status = 'ADMITTED') as admitted,
				         count(*) filter (where status = 'ENROLLED') as enrolled
				  from edushift.admission_applications
				  where tenant_id = :tenantId and academic_year_id = :yearId and deleted = false
				), evaluation_counts as (
				  select count(*) as total,
				         count(*) filter (where e.status = 'DRAFT') as draft,
				         count(*) filter (where e.status = 'PUBLISHED') as published,
				         count(*) filter (where e.status = 'CLOSED') as closed
				  from edushift.evaluations e
				  join edushift.teacher_assignments ta
				    on ta.id = e.teacher_assignment_id and ta.tenant_id = e.tenant_id and ta.deleted = false
				  join edushift.academic_periods ap
				    on ap.id = ta.academic_period_id and ap.tenant_id = ta.tenant_id and ap.deleted = false
				  where e.tenant_id = :tenantId and ap.academic_year_id = :yearId
				    and e.deleted = false and e.is_active = true
				)
				select disclosure_counts.total > 0 as disclosure_package_exists,
				       disclosure_counts.pending as disclosures_pending,
				       disclosure_counts.published as disclosures_published,
				       admission_counts.in_process as admissions_in_process,
				       admission_counts.waitlisted as admissions_waitlisted,
				       admission_counts.admitted as admissions_admitted,
				       admission_counts.enrolled as admissions_enrolled,
				       evaluation_counts.total as evaluations_total,
				       evaluation_counts.draft as evaluations_draft,
				       evaluation_counts.published as evaluations_published,
				       evaluation_counts.closed as evaluations_closed
				from disclosure_counts, admission_counts, evaluation_counts
				""",
				params,
				(rs, rowNum) -> new SchoolOperationsDashboardSummary(
						year.name(),
						rs.getBoolean("disclosure_package_exists"),
						rs.getLong("disclosures_pending"),
						rs.getLong("disclosures_published"),
						rs.getLong("admissions_in_process"),
						rs.getLong("admissions_waitlisted"),
						rs.getLong("admissions_admitted"),
						rs.getLong("admissions_enrolled"),
						rs.getLong("evaluations_total"),
						rs.getLong("evaluations_draft"),
						rs.getLong("evaluations_published"),
						rs.getLong("evaluations_closed"),
						Instant.now()));
	}

	private record AcademicYearRef(UUID id, String name) {}
}
