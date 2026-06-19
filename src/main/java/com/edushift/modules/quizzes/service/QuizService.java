package com.edushift.modules.quizzes.service;

import com.edushift.modules.quizzes.dto.AddOptionRequest;
import com.edushift.modules.quizzes.dto.CreateQuestionRequest;
import com.edushift.modules.quizzes.dto.CreateQuizRequest;
import com.edushift.modules.quizzes.dto.GradeAnswerRequest;
import com.edushift.modules.quizzes.dto.QuestionResponse;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.dto.QuizSummary;
import com.edushift.modules.quizzes.dto.UpdateQuizRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public contract for the LMS Quizzes module
 * (Sprint 7b / BE-7b.1).
 *
 * <p>Builder methods (create, patch, addQuestion, addOption,
 * publish, close, delete) are gated by {@code LMS_QUIZ_CREATE};
 * reader methods (get, list) by {@code LMS_QUIZ_READ};
 * manual grading (gradeAnswer) by {@code LMS_QUIZ_GRADE}.
 *
 * <p>All entry points assume the caller is tenant-scoped (the
 * underlying repositories auto-filter by {@code @TenantId}).
 * Cross-tenant access resolves as 404 (anti-enumeration).
 */
public interface QuizService {

	// ------------------------------------------------------------------
	// Builder (TEACHER / TENANT_ADMIN)
	// ------------------------------------------------------------------

	QuizResponse create(UUID sectionPublicUuid,
			CreateQuizRequest request, UUID ownerUserId);

	QuizResponse patch(UUID quizPublicUuid, UpdateQuizRequest request);

	QuestionResponse addQuestion(UUID quizPublicUuid,
			CreateQuestionRequest request);

	QuestionResponse addOption(UUID questionPublicUuid, AddOptionRequest request);

	QuizResponse publish(UUID quizPublicUuid);

	QuizResponse close(UUID quizPublicUuid);

	void delete(UUID quizPublicUuid);

	// ------------------------------------------------------------------
	// Reader (LMS_QUIZ_READ)
	// ------------------------------------------------------------------

	QuizResponse getByPublicUuid(UUID quizPublicUuid);

	Page<QuizSummary> listBySection(UUID sectionPublicUuid, Pageable pageable);

	// ------------------------------------------------------------------
	// Grading (LMS_QUIZ_GRADE)
	// ------------------------------------------------------------------

	/**
	 * Manually override the auto-graded points on a single answer.
	 * Auto-graded values can be overridden in either direction
	 * (correction of a wrong verdict or partial credit).
	 */
	QuizResponse gradeAnswer(UUID quizPublicUuid, UUID attemptPublicUuid,
			UUID answerPublicUuid, GradeAnswerRequest request);
}
