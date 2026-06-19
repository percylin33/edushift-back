package com.edushift.modules.quizzes.service;

import com.edushift.modules.quizzes.dto.AnswerInput;
import com.edushift.modules.quizzes.dto.AttemptResponse;
import com.edushift.modules.quizzes.dto.AttemptSummary;
import com.edushift.modules.quizzes.dto.GradingQueueItem;
import com.edushift.modules.quizzes.dto.ManualGradeAnswerRequest;
import com.edushift.modules.quizzes.dto.ManualGradeAttemptRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public contract for the LMS Quiz attempts + grading workflow
 * (Sprint 7b / BE-7b.2).
 *
 * <p>Endpoints are gated by:
 * <ul>
 *   <li>{@code LMS_QUIZ_SUBMIT} — start / getAttempt / saveAnswers
 *       / submit (taker side, STUDENT or PARENT).</li>
 *   <li>{@code LMS_QUIZ_READ} — listAttempts (TEACHER view of
 *       their section's attempts).</li>
 *   <li>{@code LMS_QUIZ_GRADE} — getGradingQueue / gradeAttempt /
 *       overrideAnswerGrade (teacher side).</li>
 * </ul>
 *
 * <p>All entry points assume the caller is tenant-scoped (the
 * underlying repositories auto-filter by {@code @TenantId}).
 * Cross-tenant access resolves as 404 (anti-enumeration).
 */
public interface QuizAttemptService {

	// ------------------------------------------------------------------
	// Taker (LMS_QUIZ_SUBMIT)
	// ------------------------------------------------------------------

	/**
	 * Start a new attempt for {@code studentUserId} on
	 * {@code quizPublicUuid}. Enforces:
	 * <ul>
	 *   <li>Quiz exists &amp; is PUBLISHED.</li>
	 *   <li>Student is currently ACTIVE-enrolled in the quiz's
	 *       section.</li>
	 *   <li>Student has not exhausted
	 *       {@code quiz.attemptsAllowed}.</li>
	 * </ul>
	 * Sets {@code expiresAt} if the quiz has a time limit.
	 */
	AttemptResponse startAttempt(UUID quizPublicUuid, UUID studentUserId,
			UUID submitterUserId);

	/**
	 * Fetch an attempt by its public UUID. Visibility is decided
	 * by the service (a STUDENT can only see their own; a TEACHER
	 * can see any in their section). The
	 * {@code revealCorrectness} flag is set accordingly.
	 */
	AttemptResponse getAttempt(UUID attemptPublicUuid, UUID callerUserId);

	/**
	 * Persist the current answer set for an IN_PROGRESS attempt.
	 * Creates new rows for unseen questions; updates existing
	 * rows in place (DB UNIQUE on {@code (attempt_id, question_id)}).
	 * Does not change the attempt's status.
	 */
	AttemptResponse saveAnswers(UUID attemptPublicUuid, UUID callerUserId,
			List<AnswerInput> answers);

	/**
	 * Final submit. Locks the attempt (status=SUBMITTED) and runs
	 * the auto-grader on every MC + TF + SHORT_ANSWER answer.
	 * Computes {@code auto_score} and transitions to
	 * {@code AUTO_GRADED} when SHORT_ANSWER questions remain, or
	 * {@code GRADED} if the quiz has no SHORT_ANSWER questions.
	 */
	AttemptResponse submitAttempt(UUID attemptPublicUuid, UUID callerUserId);

	// ------------------------------------------------------------------
	// Teacher (LMS_QUIZ_READ / LMS_QUIZ_GRADE)
	// ------------------------------------------------------------------

	/**
	 * List all attempts for a quiz, paginated. Used by the
	 * teacher-side results table.
	 */
	Page<AttemptSummary> listAttempts(UUID quizPublicUuid, Pageable pageable);

	/**
	 * List every still-ungraded SHORT_ANSWER answer for a quiz.
	 * Backed by the index
	 * {@code idx_lms_quiz_answers_tenant_pending} on
	 * {@code (tenant_id, graded_at)} (returns rows with
	 * {@code graded_at IS NULL} for the quiz's attempts).
	 */
	List<GradingQueueItem> getGradingQueue(UUID quizPublicUuid);

	/**
	 * Apply the teacher's manual grades for an entire attempt and
	 * transition it to {@code GRADED}. Recalculates
	 * {@code manual_score} and {@code score}; persists
	 * {@code feedback} and {@code gradedBy/graderAt}.
	 */
	AttemptResponse gradeAttempt(UUID attemptPublicUuid,
			ManualGradeAttemptRequest request, UUID graderUserId);

	/**
	 * Override the points on a single answer (whether auto- or
	 * manually graded). The BE-7b.1 stub is replaced by this
	 * implementation: the points are persisted on the answer row,
	 * {@code gradedBy/graderAt} are populated, and the attempt's
	 * {@code autoScore}/{@code manualScore}/{@code score} are
	 * recalculated. If the override unblocks the last pending
	 * SHORT_ANSWER the attempt may transition to
	 * {@code GRADED}.
	 */
	AttemptResponse overrideAnswerGrade(UUID quizPublicUuid,
			UUID attemptPublicUuid, UUID answerPublicUuid,
			ManualGradeAnswerRequest request, UUID graderUserId);
}
