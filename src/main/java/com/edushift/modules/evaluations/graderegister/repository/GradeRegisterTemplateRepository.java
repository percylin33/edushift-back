package com.edushift.modules.evaluations.graderegister.repository;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradeRegisterTemplateRepository
		extends JpaRepository<GradeRegisterTemplate, UUID> {

	Optional<GradeRegisterTemplate> findByPublicUuid(UUID publicUuid);

	List<GradeRegisterTemplate> findByIsSystemTrueOrderByNameAsc();

	@Query("""
			select t from GradeRegisterTemplate t
			where t.isSystem = false
			  and t.academicYear = :year
			order by t.isDefault desc, t.name asc
			""")
	List<GradeRegisterTemplate> findTenantTemplatesForYear(
			@Param("year") AcademicYear year);

	@Query("""
			select t from GradeRegisterTemplate t
			where t.isSystem = false
			  and t.academicYear = :year
			  and t.scope = com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope.TENANT
			  and t.isDefault = true
			""")
	Optional<GradeRegisterTemplate> findDefaultTenantTemplate(
			@Param("year") AcademicYear year);

	@Query("""
			select t from GradeRegisterTemplate t
			where t.isSystem = false
			  and t.academicYear = :year
			  and t.scope = com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope.COURSE
			  and t.course = :course
			""")
	Optional<GradeRegisterTemplate> findCourseTemplate(
			@Param("year") AcademicYear year,
			@Param("course") Course course);

	@Query("""
			select t from GradeRegisterTemplate t
			where t.isSystem = false
			  and t.academicYear = :year
			  and t.scope = com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope.LEVEL
			  and t.academicLevel = :level
			""")
	Optional<GradeRegisterTemplate> findLevelTemplate(
			@Param("year") AcademicYear year,
			@Param("level") AcademicLevel level);

	boolean existsByCatalogCodeIgnoreCaseAndIsSystemTrue(String catalogCode);

	Optional<GradeRegisterTemplate> findByCatalogCodeIgnoreCaseAndIsSystemTrue(String catalogCode);

	@Query("""
			select t from GradeRegisterTemplate t
			where t.isSystem = false
			  and t.academicYear = :year
			  and t.isDefault = true
			  and t.scope = com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope.TENANT
			  and t.publicUuid <> :excludeUuid
			""")
	List<GradeRegisterTemplate> findOtherDefaults(
			@Param("year") AcademicYear year,
			@Param("excludeUuid") UUID excludeUuid);
}
