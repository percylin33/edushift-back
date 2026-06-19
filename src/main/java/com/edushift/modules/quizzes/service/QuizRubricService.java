package com.edushift.modules.quizzes.service;

import com.edushift.modules.quizzes.dto.GradeWithRubricRequest;
import com.edushift.modules.quizzes.dto.QuizResponse;
import java.util.UUID;

/**
 * Bridge service between the LMS Quiz module and the
 * Evaluations/Rubric + GradeRecord modules
 * (Sprint 7b / BE-7b.3).
 *
 * <h3>Authorisation</h3>
 * <ul>
 *   <li>{@code LMS_QUIZ_CREATE} — attach / detach the rubric on
 *       a quiz (TEACHER / TENANT_ADMIN).</li>
 *   <li>{@code LMS_QUIZ_GRADE} — apply qualitative criteria to a
 *       student attempt via {@code gradeWithRubric}, which writes
 *       an additional {@code GradeRecord} on the derived
 *       {@code Evaluation}.</li>
 * </ul>
 *
 * <h3>Multi-tenant safety</h3>
 * All reads &amp; writes go through {@code @TenantId}-filtered
 * repository methods. Cross-tenant access resolves as 404
 * (anti-enumeration). The service never trusts a client-supplied
 * {@code tenant_id}; the row carries it.
 */
public interface QuizRubricService {

	/**
	 * Attach a {@code Rubric} to a quiz. Idempotent: a quiz that
	 * already carries the same rubric returns the unchanged
	 * response. Lazily creates the derived {@code Evaluation} on
	 * first attach (BE-7b.3 decision D-RUB-02).
	 */
	QuizResponse attachRubric(UUID quizPublicUuid, UUID rubricPublicUuid);

	/**
	 * Detach the current rubric. Clears
	 * {@code lms_quizzes.rubric_id} and
	 * {@code lms_quizzes.rubric_evaluation_id}. The derived
	 * evaluation is soft-deleted if and only if it has no grades
	 * yet; otherwise it stays in the grade book for historical
	 * integrity and the quiz simply unlinks.
	 */
	QuizResponse detachRubric(UUID quizPublicUuid);

	/**
	 * Apply the teacher's qualitative grading to a student
	 * attempt. Writes a new {@code GradeRecord} (literal
	 * "A" | "B" | "C" | "D") anchored to the quiz's derived
	 * {@code Evaluation} and the attempt's student. The attempt
	 * itself stays numeric-only; the rubric grade lives
	 * alongside in the grade book.
	 */
	QuizResponse gradeWithRubric(UUID attemptPublicUuid,
			GradeWithRubricRequest request, UUID graderUserId);
}
