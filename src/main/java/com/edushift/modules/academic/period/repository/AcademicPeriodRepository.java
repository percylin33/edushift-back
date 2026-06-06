package com.edushift.modules.academic.period.repository;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.year.entity.AcademicYear;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AcademicPeriod}.
 *
 * <p>Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * discriminator (the explicit {@code tenant_id = :tenant_id} clauses in
 * the native overlap query just make it explicit at the SQL layer).</p>
 */
@Repository
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, UUID> {

	Optional<AcademicPeriod> findByPublicUuid(UUID publicUuid);

	@Query("""
			select p from AcademicPeriod p
			where p.academicYear = :year
			order by p.periodType asc, p.ordinal asc
			""")
	List<AcademicPeriod> findAllByYear(@Param("year") AcademicYear year);

	@Query("""
			select p from AcademicPeriod p
			where p.academicYear = :year and p.periodType = :type
			order by p.ordinal asc
			""")
	List<AcademicPeriod> findAllByYearAndType(
			@Param("year") AcademicYear year,
			@Param("type") PeriodType type);

	@Query("""
			select coalesce(max(p.ordinal), 0) from AcademicPeriod p
			where p.academicYear = :year and p.periodType = :type
			""")
	Integer findMaxOrdinalByYearAndType(
			@Param("year") AcademicYear year,
			@Param("type") PeriodType type);

	@Query("""
			select p from AcademicPeriod p
			where p.academicYear = :year and p.periodType = :type and p.ordinal = :ordinal
			""")
	Optional<AcademicPeriod> findByYearAndTypeAndOrdinal(
			@Param("year") AcademicYear year,
			@Param("type") PeriodType type,
			@Param("ordinal") Integer ordinal);

	/**
	 * Returns periods within {@code (year, type)} whose date range
	 * overlaps {@code [start, end]}, optionally excluding a given
	 * period id (used during update). Implemented with native SQL so we
	 * can use Postgres' {@code daterange &&} operator.
	 *
	 * <p>Note: the native query relies on the inclusive bounds form
	 * ({@code daterange(..., ..., '[]')}). The {@code excludeId}
	 * parameter is nullable so the same query handles both create and
	 * update cases.</p>
	 */
	@Query(value = """
			select p.id
			from edushift.academic_periods p
			where p.deleted = false
			  and p.academic_year_id = :yearId
			  and p.period_type = :type
			  and (cast(:excludeId as uuid) is null or p.id <> cast(:excludeId as uuid))
			  and daterange(p.start_date, p.end_date, '[]')
			   && daterange(cast(:start as date), cast(:end as date), '[]')
			limit 1
			""", nativeQuery = true)
	Optional<UUID> findOverlap(
			@Param("yearId") UUID yearId,
			@Param("type") String type,
			@Param("start") LocalDate start,
			@Param("end") LocalDate end,
			@Param("excludeId") UUID excludeId);
}
