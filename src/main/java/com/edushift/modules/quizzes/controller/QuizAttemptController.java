package com.edushift.modules.quizzes.controller;

import com.edushift.modules.quizzes.dto.AttemptResponse;
import com.edushift.modules.quizzes.dto.AttemptSummary;
import com.edushift.modules.quizzes.dto.GradeWithRubricRequest;
import com.edushift.modules.quizzes.dto.GradingQueueItem;
import com.edushift.modules.quizzes.dto.ManualGradeAttemptRequest;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.dto.SaveAnswersRequest;
import com.edushift.modules.quizzes.service.QuizAttemptService;
import com.edushift.modules.quizzes.service.QuizRubricService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the LMS Quiz attempts + manual grading flow
 * (Sprint 7b / BE-7b.2). Endpoints:
 *
 * <table>
 *   <caption>Quiz attempt + grading endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Authority</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/quizzes/{quizUuid}/attempts</td>
 *       <td>LMS_QUIZ_SUBMIT</td>
 *       <td>{@link AttemptResponse} (201)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/attempts/{attemptUuid}</td>
 *       <td>LMS_QUIZ_SUBMIT (taker) / LMS_QUIZ_GRADE (grader)</td>
 *       <td>{@link AttemptResponse} (200)</td></tr>
 *   <tr><td>PATCH</td>
 *       <td>/attempts/{attemptUuid}</td>
 *       <td>LMS_QUIZ_SUBMIT</td>
 *       <td>{@link AttemptResponse} (200)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/attempts/{attemptUuid}/submit</td>
 *       <td>LMS_QUIZ_SUBMIT</td>
 *       <td>{@link AttemptResponse} (200)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/quizzes/{quizUuid}/attempts</td>
 *       <td>LMS_QUIZ_READ</td>
 *       <td>{@code Page<}{@link AttemptSummary}{@code >} (200)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/quizzes/{quizUuid}/grading-queue</td>
 *       <td>LMS_QUIZ_GRADE</td>
 *       <td>{@code List<}{@link GradingQueueItem}{@code >} (200)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/attempts/{attemptUuid}/grade</td>
 *       <td>LMS_QUIZ_GRADE</td>
 *       <td>{@link AttemptResponse} (200)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/attempts/{attemptUuid}/grade-with-rubric</td>
 *       <td>LMS_QUIZ_GRADE</td>
 *       <td>{@link QuizResponse} (200) — BE-7b.3 rubric grading</td></tr>
 * </table>
 *
 * <h3>Authorisation</h3>
 * Coarse-grained {@code hasAuthority(...)} at the controller; the
 * service performs the fine-grained ownership and enrollment
 * checks.
 *
 * <h3>Cross-tenant</h3>
 * All lookups go through tenant-aware repositories; cross-tenant
 * access resolves as 404 (anti-enumeration).
 *
 * <h3>Override endpoint</h3>
 * The single-answer override
 * ({@code PATCH /quizzes/{quizUuid}/attempts/{attemptUuid}/answers/{answerUuid}})
 * still lives in {@code QuizController} to keep the BE-7b.1 URL
 * stable; the underlying implementation is delegated to
 * {@link QuizAttemptService#overrideAnswerGrade}.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Quiz Attempts")
public class QuizAttemptController {

	private final QuizAttemptService attemptService;
	private final QuizRubricService quizRubricService;
	private final CurrentUserProvider currentUserProvider;

	@PostMapping("/quizzes/{quizUuid}/attempts")
	@PreAuthorize("hasAuthority('LMS_QUIZ_SUBMIT')")
	@Operation(summary = "Start a new attempt (taker side). Enforces PUBLISHED state, ACTIVE enrollment and attempts_allowed.")
	public ResponseEntity<ApiResponse<AttemptResponse>> start(
			@PathVariable UUID quizUuid) {
		UUID caller = currentUserProvider.currentUserId().orElse(null);
		AttemptResponse response = attemptService.startAttempt(
				quizUuid, caller, caller);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(response));
	}

	@GetMapping("/attempts/{attemptUuid}")
	@PreAuthorize("hasAnyAuthority('LMS_QUIZ_SUBMIT','LMS_QUIZ_GRADE')")
	@Operation(summary = "Fetch a single attempt with all answers. Visibility decided by the service (taker sees their own; grader sees any).")
	public ApiResponse<AttemptResponse> get(@PathVariable UUID attemptUuid) {
		UUID caller = currentUserProvider.currentUserId().orElse(null);
		return ApiResponse.ok(attemptService.getAttempt(attemptUuid, caller));
	}

	@PatchMapping("/attempts/{attemptUuid}")
	@PreAuthorize("hasAuthority('LMS_QUIZ_SUBMIT')")
	@Operation(summary = "Autosave answers on an IN_PROGRESS attempt. UPSERT semantics on (attempt, question).")
	public ApiResponse<AttemptResponse> saveAnswers(
			@PathVariable UUID attemptUuid,
			@Valid @RequestBody SaveAnswersRequest request) {
		UUID caller = currentUserProvider.currentUserId().orElse(null);
		return ApiResponse.ok(
				attemptService.saveAnswers(attemptUuid, caller, request.answers()));
	}

	@PostMapping("/attempts/{attemptUuid}/submit")
	@PreAuthorize("hasAuthority('LMS_QUIZ_SUBMIT')")
	@Operation(summary = "Final submit. Locks the attempt and runs the auto-grader (MC + TF + SHORT_ANSWER seed).")
	public ApiResponse<AttemptResponse> submit(@PathVariable UUID attemptUuid) {
		UUID caller = currentUserProvider.currentUserId().orElse(null);
		return ApiResponse.ok(attemptService.submitAttempt(attemptUuid, caller));
	}

	@GetMapping("/quizzes/{quizUuid}/attempts")
	@PreAuthorize("hasAuthority('LMS_QUIZ_READ')")
	@Operation(summary = "List all attempts for a quiz, paginated (teacher side).")
	public Page<AttemptSummary> list(
			@PathVariable UUID quizUuid,
			@PageableDefault(size = 20) Pageable pageable) {
		return attemptService.listAttempts(quizUuid, pageable);
	}

	@GetMapping("/quizzes/{quizUuid}/grading-queue")
	@PreAuthorize("hasAuthority('LMS_QUIZ_GRADE')")
	@Operation(summary = "List the ungraded SHORT_ANSWER answers for a quiz (teacher queue).")
	public ApiResponse<List<GradingQueueItem>> queue(@PathVariable UUID quizUuid) {
		return ApiResponse.ok(attemptService.getGradingQueue(quizUuid));
	}

	@PostMapping("/attempts/{attemptUuid}/grade")
	@PreAuthorize("hasAuthority('LMS_QUIZ_GRADE')")
	@Operation(summary = "Apply manual grades to an attempt's pending answers and transition the attempt to GRADED.")
	public ApiResponse<AttemptResponse> grade(
			@PathVariable UUID attemptUuid,
			@Valid @RequestBody ManualGradeAttemptRequest request) {
		UUID grader = currentUserProvider.currentUserId().orElse(null);
		return ApiResponse.ok(
				attemptService.gradeAttempt(attemptUuid, request, grader));
	}

	// ------------------------------------------------------------------
	// Rubric bridge (Sprint 7b / BE-7b.3)
	// ------------------------------------------------------------------

	@PostMapping("/attempts/{attemptUuid}/grade-with-rubric")
	@PreAuthorize("hasAuthority('LMS_QUIZ_GRADE')")
	@Operation(summary = "Apply the teacher's qualitative rubric grading to an attempt. "
			+ "Writes a new GradeRecord (literal A | B | C | D) anchored to the quiz's "
			+ "derived Evaluation (BE-7b.3). The attempt itself stays numeric-only.")
	public ApiResponse<QuizResponse> gradeWithRubric(
			@PathVariable UUID attemptUuid,
			@Valid @RequestBody GradeWithRubricRequest request) {
		UUID grader = currentUserProvider.currentUserId().orElse(null);
		return ApiResponse.ok(
				quizRubricService.gradeWithRubric(attemptUuid, request, grader));
	}
}
