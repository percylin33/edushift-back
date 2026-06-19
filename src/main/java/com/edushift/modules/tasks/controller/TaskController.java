package com.edushift.modules.tasks.controller;

import com.edushift.modules.tasks.dto.CreateTaskRequest;
import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.TaskSummary;
import com.edushift.modules.tasks.dto.UpdateTaskRequest;
import com.edushift.modules.tasks.service.TaskService;
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
 * REST adapter for the LMS tasks module (Sprint 7a / BE-7a.2).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Task endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Authority</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/sections/{sectionPublicUuid}/tasks</td>
 *       <td>LMS_TASK_CREATE</td>
 *       <td>{@link TaskResponse} (201)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/sections/{sectionPublicUuid}/tasks</td>
 *       <td>LMS_TASK_READ</td>
 *       <td>{@code Page<}{@link TaskSummary}{@code >} (200)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/tasks/{publicUuid}</td>
 *       <td>LMS_TASK_READ</td>
 *       <td>{@link TaskResponse} (200)</td></tr>
 *   <tr><td>PATCH</td>
 *       <td>/tasks/{publicUuid}</td>
 *       <td>LMS_TASK_CREATE (owner / admin)</td>
 *       <td>{@link TaskResponse} (200)</td></tr>
 *   <tr><td>DELETE</td>
 *       <td>/tasks/{publicUuid}</td>
 *       <td>LMS_TASK_CREATE (owner / admin)</td>
 *       <td>(204)</td></tr>
 * </table>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Tasks")
public class TaskController {

	private final TaskService taskService;
	private final CurrentUserProvider currentUserProvider;

	@PostMapping("/sections/{sectionPublicUuid}/tasks")
	@PreAuthorize("hasAuthority('LMS_TASK_CREATE')")
	@Operation(summary = "Create a task (assignment) in a section")
	public ResponseEntity<ApiResponse<TaskResponse>> create(
			@PathVariable UUID sectionPublicUuid,
			@Valid @RequestBody CreateTaskRequest request) {
		UUID owner = currentUserProvider.currentUserId().orElse(null);
		TaskResponse response = taskService.create(sectionPublicUuid, request, owner);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@GetMapping("/sections/{sectionPublicUuid}/tasks")
	@PreAuthorize("hasAuthority('LMS_TASK_READ')")
	@Operation(summary = "List tasks of a section (paged)")
	public Page<TaskSummary> list(
			@PathVariable UUID sectionPublicUuid,
			@PageableDefault(size = 20) Pageable pageable) {
		return taskService.listBySection(sectionPublicUuid, pageable);
	}

	@GetMapping("/tasks/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_TASK_READ')")
	@Operation(summary = "Fetch a task by its public UUID")
	public ApiResponse<TaskResponse> get(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(taskService.getByPublicUuid(publicUuid));
	}

	@PatchMapping("/tasks/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_TASK_CREATE')")
	@Operation(summary = "Patch a task (title, description, dueAt, attachment, allowResubmission)")
	public ApiResponse<TaskResponse> patch(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateTaskRequest request) {
		return ApiResponse.ok(taskService.patch(publicUuid, request));
	}

	@DeleteMapping("/tasks/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_TASK_CREATE')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Soft-delete a task. Submissions remain (orphan pattern, D-TSK-05).")
	public void delete(@PathVariable UUID publicUuid) {
		taskService.delete(publicUuid);
	}
}
