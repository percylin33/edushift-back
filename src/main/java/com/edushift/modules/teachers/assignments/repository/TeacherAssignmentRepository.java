package com.edushift.modules.teachers.assignments.repository;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link TeacherAssignment}. Tenant-scoped via
 * Hibernate's {@code @TenantId} discriminator.
 *
 * <p>Every read query filters {@code unassignedAt is null} unless the
 * caller explicitly asks for the full history. The "active or all"
 * decision belongs to the service, the repo just exposes both shapes
 * to keep the SQL inspectable.</p>
 */
@Repository
public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, UUID> {

	Optional<TeacherAssignment> findByPublicUuid(UUID publicUuid);

	/**
	 * Looks up the active assignment that matches a 4-tuple, used to
	 * surface a clear {@code ASSIGNMENT_ALREADY_ACTIVE} (409) instead
	 * of a generic constraint violation when the user retries.
	 */
	@Query("""
			select a from TeacherAssignment a
			where a.teacher = :teacher
			  and a.section = :section
			  and a.course  = :course
			  and a.academicPeriod = :period
			  and a.unassignedAt is null
			""")
	Optional<TeacherAssignment> findActiveTuple(
			@Param("teacher") Teacher teacher,
			@Param("section") Section section,
			@Param("course") Course course,
			@Param("period") AcademicPeriod period);

	/**
	 * Teacher-centric list. Filters compose with AND:
	 * <ul>
	 *   <li>{@code period} — when non-null, narrows to a single period.</li>
	 *   <li>{@code activeOnly} — when true, hides historical (soft-ended)
	 *       rows.</li>
	 * </ul>
	 * Ordered by period ordinal asc, then assignedAt desc.
	 */
	@Query("""
			select a from TeacherAssignment a
			where a.teacher = :teacher
			  and (:period is null or a.academicPeriod = :period)
			  and (:activeOnly = false or a.unassignedAt is null)
			order by a.academicPeriod.periodType asc,
			         a.academicPeriod.ordinal asc,
			         a.assignedAt desc
			""")
	List<TeacherAssignment> findAllByTeacher(
			@Param("teacher") Teacher teacher,
			@Param("period") AcademicPeriod period,
			@Param("activeOnly") boolean activeOnly);

	/**
	 * Section-centric list (reverse view). Always returns active rows
	 * because the use case ("who teaches in this section?") is forward-
	 * looking. Optionally narrowed to a single period.
	 */
	@Query("""
			select a from TeacherAssignment a
			where a.section = :section
			  and (:period is null or a.academicPeriod = :period)
			  and a.unassignedAt is null
			order by a.academicPeriod.periodType asc,
			         a.academicPeriod.ordinal asc,
			         a.assignedAt asc
			""")
	List<TeacherAssignment> findAllBySectionActive(
			@Param("section") Section section,
			@Param("period") AcademicPeriod period);

	/**
	 * Existence check used by {@code TeacherServiceImpl.deleteTeacher}
	 * to surface {@code TEACHER_HAS_ACTIVE_ASSIGNMENTS} (409).
	 */
	@Query("""
			select count(a) > 0 from TeacherAssignment a
			where a.teacher = :teacher and a.unassignedAt is null
			""")
	boolean existsActiveByTeacher(@Param("teacher") Teacher teacher);

	/**
	 * Existence check used by {@code CourseServiceImpl.deleteCourse}.
	 */
	@Query("""
			select count(a) > 0 from TeacherAssignment a
			where a.course = :course and a.unassignedAt is null
			""")
	boolean existsActiveByCourse(@Param("course") Course course);

	/**
	 * Existence check used by {@code AcademicPeriodServiceImpl.deletePeriod}
	 * (DEBT-ACAD-4).
	 */
	@Query("""
			select count(a) > 0 from TeacherAssignment a
			where a.academicPeriod = :period and a.unassignedAt is null
			""")
	boolean existsActiveByPeriod(@Param("period") AcademicPeriod period);
}
