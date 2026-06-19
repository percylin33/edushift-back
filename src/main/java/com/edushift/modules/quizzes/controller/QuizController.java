package com.edushift.modules.quizzes.controller;

import com.edushift.modules.quizzes.dto.AddOptionRequest;
import com.edushift.modules.quizzes.dto.AttachRubricRequest;
import com.edushift.modules.quizzes.dto.CreateQuestionRequest;
import com.edushift.modules.quizzes.dto.CreateQuizRequest;
import com.edushift.modules.quizzes.dto.GradeAnswerRequest;
import com.edushift.modules.quizzes.dto.QuestionResponse;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.dto.QuizSummary;
import com.edushift.modules.quizzes.dto.UpdateQuizRequest;
import com.edushift.modules.quizzes.service.QuizRubricService;
import com.edushift.modules.quizzes.service.QuizService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the LMS Quizzes module (Sprint 7b / BE-7b.1).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Quiz endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Authority</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/sections/{sectionPublicUuid}/quizzes</td>
 *       <td>LMS_QUIZ_CREATE</td>
 *       <td>{@link QuizResponse} (201)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/sections/{sectionPublicUuid}/quizzes</td>
 *       <td>LMS_QUIZ_READ</td>
 *       <td>{@code Page<}{@link QuizSummary}{@code >} (200)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/quizzes/{publicUuid}</td>
 *       <td>LMS_QUIZ_READ</td>
 *       <td>{@link QuizResponse} (200)</td></tr>
 *   <tr><td>PATCH</td>
 *       <td>/quizzes/{publicUuid}</td>
 *       <td>LMS_QUIZ_CREATE</td>
 *       <td>{@link QuizResponse} (200)</td></tr>
 *   <tr><td>DELETE</td>
 *       <td>/quizzes/{publicUuid}</td>
 *       <td>LMS_QUIZ_CREATE</td>
 *       <td>(204)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/quizzes/{publicUuid}/questions</td>
 *       <td>LMS_QUIZ_CREATE</td>
 *       <td>{@link QuestionResponse} (201)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/questions/{publicUuid}/options</td>
 *       <td>LMS_QUIZ_CREATE</td>
 *       <td>{@link QuestionResponse} (201)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/quizzes/{publicUuid}/publish</td>
 *       <td>LMS_QUIZ_CREATE</td>
 *       <td>{@link QuizResponse} (200)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/quizzes/{publicUuid}/close</td>
 *       <td>LMS_QUIZ_CREATE</td>
 *       <td>{@link QuizResponse} (200)</td></tr>
 *   <tr><td>PATCH</td>
 *       <td>/quizzes/{quizUuid}/attempts/{attemptUuid}/answers/{answerUuid}</td>
 *       <td>LMS_QUIZ_GRADE</td>
 *       <td>{@link QuizResponse} (200)</td></tr>
 *   <tr><td>PATCH</td>
 *       <td>/quizzes/{uuid}/rubric</td>
 *       <td>LMS_QUIZ_CREATE</td>
 *       <td>{@link QuizResponse} (200) — BE-7b.3 rubric attach</td></tr>
 * </table>
 *
 * <h3>Authorisation</h3>
 * Coarse-grained {@code hasAuthority(...)} at the controller; the
 * service performs the fine-grained state checks (DRAFT vs
 * PUBLISHED, MC invariants, etc.).
 *
 * <h3>Cross-tenant</h3>
 * Section and quiz lookups go through tenant-aware repositories;
 * cross-tenant access resolves as 404 (anti-enumeration).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Quizzes")
public class QuizController {

	private final QuizService quizService;
	private final QuizRubricService quizRubricService;
	private final CurrentUserProvider currentUserProvider;

	@PostMapping("/sections/{sectionPublicUuid}/quizzes")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@Operation(summary = "Create a quiz (and optionally its questions) in a section")
	public ResponseEntity<ApiResponse<QuizResponse>> create(
			@PathVariable UUID sectionPublicUuid,
			@Valid @RequestBody CreateQuizRequest request) {
		UUID owner = currentUserProvider.currentUserId().orElse(null);
		QuizResponse response = quizService.create(sectionPublicUuid, request, owner);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@GetMapping("/sections/{sectionPublicUuid}/quizzes")
	@PreAuthorize("hasAuthority('LMS_QUIZ_READ')")
	@Operation(summary = "List quizzes of a section (paged)")
	public Page<QuizSummary> list(
			@PathVariable UUID sectionPublicUuid,
			@PageableDefault(size = 20) Pageable pageable) {
		return quizService.listBySection(sectionPublicUuid, pageable);
	}

	@GetMapping("/quizzes/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_QUIZ_READ')")
	@Operation(summary = "Fetch a quiz by its public UUID (with questions + options)")
	public ApiResponse<QuizResponse> get(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(quizService.getByPublicUuid(publicUuid));
	}

	@PatchMapping("/quizzes/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@Operation(summary = "Patch a quiz (title, description, dueAt, timeLimit, maxAttempts, maxScore)")
	public ApiResponse<QuizResponse> patch(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateQuizRequest request) {
		return ApiResponse.ok(quizService.patch(publicUuid, request));
	}

	@DeleteMapping("/quizzes/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Soft-delete a quiz. Attempts remain (orphan pattern, D-QUIZ-01).")
	public void delete(@PathVariable UUID publicUuid) {
		quizService.delete(publicUuid);
	}

	@PostMapping("/quizzes/{publicUuid}/questions")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@Operation(summary = "Add a question to a DRAFT quiz")
	public ResponseEntity<ApiResponse<QuestionResponse>> addQuestion(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody CreateQuestionRequest request) {
		QuestionResponse response = quizService.addQuestion(publicUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PostMapping("/questions/{publicUuid}/options")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@Operation(summary = "Add an option to a DRAFT MC question (validates the exactly-one-correct invariant on the full set)")
	public ResponseEntity<ApiResponse<QuestionResponse>> addOption(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody AddOptionRequest request) {
		QuestionResponse response = quizService.addOption(publicUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PostMapping("/quizzes/{publicUuid}/publish")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@Operation(summary = "Publish a DRAFT quiz (sets status=PUBLISHED, publishedAt=now). Requires at least one question.")
	public ApiResponse<QuizResponse> publish(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(quizService.publish(publicUuid));
	}

	@PostMapping("/quizzes/{publicUuid}/close")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@Operation(summary = "Close a PUBLISHED quiz (sets status=CLOSED, closedAt=now). Idempotent failure on DRAFT/CLOSED.")
	public ApiResponse<QuizResponse> close(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(quizService.close(publicUuid));
	}

	@PatchMapping("/quizzes/{quizUuid}/attempts/{attemptUuid}/answers/{answerUuid}")
	@PreAuthorize("hasAuthority('LMS_QUIZ_GRADE')")
	@Operation(summary = "Manually override the points awarded on a single answer. Implementation lives in QuizAttemptService.overrideAnswerGrade (BE-7b.2; closes DEBT-BE-7B-5).")
	public ApiResponse<QuizResponse> gradeAnswer(
			@PathVariable UUID quizUuid,
			@PathVariable UUID attemptUuid,
			@PathVariable UUID answerUuid,
			@Valid @RequestBody GradeAnswerRequest request) {
		return ApiResponse.ok(
				quizService.gradeAnswer(quizUuid, attemptUuid, answerUuid, request));
	}

	// ------------------------------------------------------------------
	// Rubric bridge (Sprint 7b / BE-7b.3)
	// ------------------------------------------------------------------

	@PatchMapping("/quizzes/{publicUuid}/rubric")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@Operation(summary = "Attach (or replace) the qualitative rubric for this quiz. "
			+ "Idempotent: re-attaching the same rubric returns the unchanged response. "
			+ "Lazily creates a derived Evaluation row on first attach (BE-7b.3 D-RUB-02).")
	public ApiResponse<QuizResponse> attachRubric(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody AttachRubricRequest request) {
		return ApiResponse.ok(
				quizRubricService.attachRubric(publicUuid, request.rubricPublicUuid()));
	}

	@DeleteMapping("/quizzes/{publicUuid}/rubric")
	@PreAuthorize("hasAuthority('LMS_QUIZ_CREATE')")
	@Operation(summary = "Detach the rubric from a quiz. Idempotent: a quiz without a "
			+ "rubric returns the unchanged response.")
	public ApiResponse<QuizResponse> detachRubric(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(quizRubricService.detachRubric(publicUuid));
	}
}
