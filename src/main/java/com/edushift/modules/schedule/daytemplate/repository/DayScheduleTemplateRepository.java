package com.edushift.modules.schedule.daytemplate.repository;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DayScheduleTemplateRepository extends JpaRepository<DayScheduleTemplate, UUID> {

	Optional<DayScheduleTemplate> findByPublicUuid(UUID publicUuid);

	List<DayScheduleTemplate> findByAcademicYear_PublicUuidOrderByNameAsc(UUID yearPublicUuid);

	@Query("""
			select t from DayScheduleTemplate t
			where t.academicYear = :year
			  and t.academicLevel = :level
			  and t.grade = :grade
			  and ((:shift is null and t.shift is null) or t.shift = :shift)
			""")
	Optional<DayScheduleTemplate> findGradeSpecific(
			@Param("year") AcademicYear year,
			@Param("level") AcademicLevel level,
			@Param("grade") Grade grade,
			@Param("shift") String shift);

	@Query("""
			select t from DayScheduleTemplate t
			where t.academicYear = :year
			  and t.academicLevel = :level
			  and t.grade is null
			  and ((:shift is null and t.shift is null) or t.shift = :shift)
			""")
	Optional<DayScheduleTemplate> findLevelDefault(
			@Param("year") AcademicYear year,
			@Param("level") AcademicLevel level,
			@Param("shift") String shift);

	List<DayScheduleTemplate> findAllByAcademicYear(AcademicYear year);
}
