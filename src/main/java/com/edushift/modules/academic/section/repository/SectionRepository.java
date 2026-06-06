package com.edushift.modules.academic.section.repository;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Section}. Tenant-scoped automatically
 * by Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface SectionRepository extends JpaRepository<Section, UUID> {

	Optional<Section> findByPublicUuid(UUID publicUuid);

	@Query("""
			select s from Section s
			where s.academicYear = :year
			  and s.grade = :grade
			  and lower(s.name) = lower(:name)
			""")
	Optional<Section> findByYearGradeAndNameIgnoreCase(
			@Param("year") AcademicYear year,
			@Param("grade") Grade grade,
			@Param("name") String name);

	List<Section> findAllByAcademicYearOrderByDisplayOrderAscNameAsc(AcademicYear academicYear);

	List<Section> findAllByAcademicYearAndGradeOrderByDisplayOrderAscNameAsc(
			AcademicYear academicYear, Grade grade);

	@Query("""
			select s from Section s
			where s.academicYear = :year
			  and s.grade.level = :level
			order by s.grade.ordinal asc, s.displayOrder asc, s.name asc
			""")
	List<Section> findAllByYearAndLevel(
			@Param("year") AcademicYear year,
			@Param("level") AcademicLevel level);

	long countByGrade(Grade grade);

	long countByAcademicYear(AcademicYear academicYear);
}
