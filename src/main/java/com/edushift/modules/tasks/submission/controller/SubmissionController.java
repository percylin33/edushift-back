package com.edushift.modules.tasks.submission.controller;

import com.edushift.modules.tasks.submission.dto.CreateSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.GradeSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.SubmissionResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionSummary;
import com.edushift.modules.tasks.submission.service.SubmissionService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the LMS submissions sub-module
 * (Sprint 7a / BE-7a.2).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Submission endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Authority</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/tasks/{taskPublicUuid}/submissions</td>
 *       <td>LMS_TASK_SUBMIT</td>
 *       <td>{@link SubmissionResponse} (201/200)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/tasks/{taskPublicUuid}/submissions</td>
 *       <td>LMS_TASK_GRADE</td>
 *       <td>{@code Page<}{@link SubmissionSummary}{@code >} (200)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/tasks/{taskPublicUuid}/submissions/me</td>
 *       <td>LMS_TASK_SUBMIT</td>
 *       <td>{@link SubmissionResponse} or 200 with {@code data:null} (200)</td></tr>
 *   <tr><td>PATCH</td>
 *       <td>/submissions/{publicUuid}/grade</td>
 *       <td>LMS_TASK_GRADE</td>
 *       <td>{@link SubmissionResponse} (200)</td></tr>
 * </table>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Submissions")
public class SubmissionController {

	private final SubmissionService submissionService;
	private final CurrentUserProvider currentUserProvider;

	@PostMapping("/tasks/{taskPublicUuid}/submissions")
	@PreAuthorize("hasAuthority('LMS_TASK_SUBMIT')")
	@Operation(summary = "Submit (or re-submit) on behalf of a student")
	public ResponseEntity<ApiResponse<SubmissionResponse>> submit(
			@PathVariable UUID taskPublicUuid,
			@Valid @RequestBody CreateSubmissionRequest request) {
		UUID submitter = currentUserProvider.currentUserId().orElse(null);
		SubmissionResponse response = submissionService.submit(
				taskPublicUuid, request, submitter);
		HttpStatus status = Boolean.TRUE.equals(response.wasIdempotent())
				? HttpStatus.OK
				: HttpStatus.CREATED;
		return ResponseEntity.status(status).body(ApiResponse.ok(response));
	}

	@GetMapping("/tasks/{taskPublicUuid}/submissions")
	@PreAuthorize("hasAuthority('LMS_TASK_GRADE')")
	@Operation(summary = "List submissions of a task (teacher / admin)")
	public Page<SubmissionSummary> list(
			@PathVariable UUID taskPublicUuid,
			@PageableDefault(size = 20) Pageable pageable) {
		return submissionService.listByTask(taskPublicUuid, pageable);
	}

	@GetMapping("/tasks/{taskPublicUuid}/submissions/me")
	@PreAuthorize("hasAuthority('LMS_TASK_SUBMIT')")
	@Operation(summary = "Fetch the current submission for a student")
	public ApiResponse<SubmissionResponse> getMine(
			@PathVariable UUID taskPublicUuid,
			@RequestParam(value = "studentPublicUuid", required = false) UUID studentPublicUuid) {
		// Self-submit default: when the bearer is a STUDENT, the
		// target is themselves. When the bearer is a PARENT, they
		// MUST pass studentPublicUuid explicitly.
		UUID target = studentPublicUuid != null
				? studentPublicUuid
				: currentUserProvider.currentUserId().orElse(null);
		return ApiResponse.ok(submissionService.getMine(taskPublicUuid, target));
	}

	@PatchMapping("/submissions/{publicUuid}/grade")
	@PreAuthorize("hasAuthority('LMS_TASK_GRADE')")
	@Operation(summary = "Grade a submission")
	public ApiResponse<SubmissionResponse> grade(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody GradeSubmissionRequest request) {
		UUID grader = currentUserProvider.currentUserId().orElse(null);
		return ApiResponse.ok(submissionService.grade(publicUuid, request, grader));
	}
}
