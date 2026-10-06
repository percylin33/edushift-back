package com.edushift.modules.quizzes.repository;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Quiz} (Sprint 7b / BE-7b.0 + V105).
 */
@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {

	Optional<Quiz> findByPublicUuid(UUID publicUuid);

	boolean existsByPublicUuid(UUID publicUuid);

	@EntityGraph(attributePaths = {
			"teacherAssignment",
			"teacherAssignment.course",
			"teacherAssignment.academicPeriod"
	})
	Page<Quiz> findAllBySectionOrderByDueAtDesc(Section section, Pageable pageable);

	Page<Quiz> findAllBySectionAndStatusOrderByDueAtDesc(
			Section section, QuizStatus status, Pageable pageable);

	@EntityGraph(attributePaths = {
			"teacherAssignment",
			"teacherAssignment.course",
			"teacherAssignment.academicPeriod"
	})
	Page<Quiz> findAllBySectionAndTeacherAssignmentOrderByDueAtDesc(
			Section section, TeacherAssignment teacherAssignment, Pageable pageable);

	Page<Quiz> findAllBySectionAndTeacherAssignmentAndStatusOrderByDueAtDesc(
			Section section,
			TeacherAssignment teacherAssignment,
			QuizStatus status,
			Pageable pageable);

	long countBySectionAndTeacherAssignment(Section section, TeacherAssignment teacherAssignment);

	long countBySectionAndTeacherAssignmentAndStatus(
			Section section, TeacherAssignment teacherAssignment, QuizStatus status);
}
