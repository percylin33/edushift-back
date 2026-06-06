package com.edushift.modules.academic.course.repository;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for the {@link CourseLevel} pivot.
 *
 * <p>Tenant-scoped via Hibernate's {@code @TenantId} discriminator.</p>
 */
@Repository
public interface CourseLevelRepository extends JpaRepository<CourseLevel, UUID> {

	List<CourseLevel> findAllByCourse(Course course);

	@Query("""
			select cl from CourseLevel cl
			where cl.course in :courses
			""")
	List<CourseLevel> findAllByCourses(@Param("courses") List<Course> courses);

	long countByLevel(AcademicLevel level);

	/**
	 * Used by BE-4.7 ({@code TeacherAssignment} validation) to check
	 * whether a course is applicable to a given level. The pair is
	 * unique on non-deleted rows so this is a single-row probe.
	 */
	@Query("""
			select count(cl) > 0 from CourseLevel cl
			where cl.course = :course and cl.level = :level
			""")
	boolean existsByCourseAndLevel(
			@Param("course") Course course,
			@Param("level") AcademicLevel level);
}
