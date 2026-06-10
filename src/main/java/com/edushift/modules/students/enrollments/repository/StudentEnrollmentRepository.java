package com.edushift.modules.students.enrollments.repository;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.entity.Student;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link StudentEnrollment}. Tenant-scoped via
 * Hibernate's {@code @TenantId} discriminator.
 *
 * <h3>Active vs historic</h3>
 * Most read queries provide both shapes. The service layer is the single
 * place that decides which one to expose to a given endpoint:
 * <ul>
 *   <li>{@link #findActiveByStudentAndYear} — used to enforce
 *       "one ACTIVE per (student, year)" before insert and to back the
 *       {@code currentSectionId} / {@code currentAcademicYearId} filters
 *       on the {@code GET /students} endpoint.</li>
 *   <li>{@link #findAllByStudent} — full history, ordered desc.</li>
 *   <li>{@link #findActiveBySection} — section roster (current).</li>
 *   <li>{@link #existsActiveBySection} — backs the
 *       {@code SECTION_HAS_ENROLLMENTS} delete-guard.</li>
 * </ul>
 */
@Repository
public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, UUID> {

	Optional<StudentEnrollment> findByPublicUuid(UUID publicUuid);

	/**
	 * Looks up the active enrollment for {@code (student, year)}; the
	 * unique partial index in V20 guarantees this is at most one row.
	 */
	@Query("""
			select e from StudentEnrollment e
			where e.student = :student
			  and e.academicYear = :year
			  and e.status = com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus.ACTIVE
			""")
	Optional<StudentEnrollment> findActiveByStudentAndYear(
			@Param("student") Student student,
			@Param("year") AcademicYear year);

	/**
	 * Full enrollment timeline for a single student, newest first.
	 * Ordered by {@code enrolledAt desc} then {@code createdAt desc}
	 * to keep the order stable for rows created on the same day.
	 */
	@Query("""
			select e from StudentEnrollment e
			where e.student = :student
			order by e.enrolledAt desc, e.createdAt desc
			""")
	List<StudentEnrollment> findAllByStudent(@Param("student") Student student);

	/**
	 * Active roster of a section (current students). Ordered by the
	 * student's last name to render alphabetically without an extra sort.
	 */
	@Query("""
			select e from StudentEnrollment e
			where e.section = :section
			  and e.status = com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus.ACTIVE
			order by e.student.lastName asc, e.student.firstName asc
			""")
	List<StudentEnrollment> findActiveBySection(@Param("section") Section section);

	/**
	 * Existence check used by {@code SectionServiceImpl.deleteSection}
	 * to surface {@code SECTION_HAS_ENROLLMENTS} (409).
	 */
	@Query("""
			select count(e) > 0 from StudentEnrollment e
			where e.section = :section
			  and e.status = com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus.ACTIVE
			""")
	boolean existsActiveBySection(@Param("section") Section section);

	/**
	 * Was the student ACTIVE-enrolled in the given section on
	 * {@code date}? "Active on a date" means: the row's
	 * {@code enrolledAt <= date} and either {@code withdrawnAt} is
	 * {@code null} or strictly after {@code date}, regardless of the
	 * enrollment's current status (a transferred student is still
	 * "enrolled at" the day before transfer).
	 *
	 * <p>Backs the {@code GRADE_STUDENT_NOT_ENROLLED} guard in the
	 * {@code evaluations.graderecord} sub-module (BE-5B.3): a teacher
	 * cannot register a grade for a student who wasn't part of the
	 * section on the evaluation's {@code scheduledDate}.
	 */
	@Query("""
			select count(e) > 0 from StudentEnrollment e
			where e.student = :student
			  and e.section = :section
			  and e.enrolledAt <= :date
			  and (e.withdrawnAt is null or e.withdrawnAt > :date)
			""")
	boolean existsActiveAt(
			@Param("student") Student student,
			@Param("section") Section section,
			@Param("date") LocalDate date);
}
