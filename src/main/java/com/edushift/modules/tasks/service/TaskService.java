package com.edushift.modules.tasks.service;

import com.edushift.modules.tasks.dto.CreateTaskRequest;
import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.TaskSummary;
import com.edushift.modules.tasks.dto.UpdateTaskRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public contract for the LMS tasks module (Sprint 7a / BE-7a.2).
 *
 * <p>Builder methods (create, patch, publish, archive, delete) are
 * gated by {@code LMS_TASK_CREATE}; reader methods (get, list) by
 * {@code LMS_TASK_READ}.
 *
 * <p>Publish/archive were added as part of BUG-2026-07-31-04.
 */
public interface TaskService {

	TaskResponse create(UUID sectionPublicUuid, CreateTaskRequest request, UUID ownerUserId);

	Page<TaskSummary> listBySection(UUID sectionPublicUuid, Pageable pageable);

	TaskResponse getByPublicUuid(UUID publicUuid);

	TaskResponse patch(UUID publicUuid, UpdateTaskRequest request);

	/**
	 * Transition a DRAFT task to PUBLISHED. Idempotent failure on
	 * non-DRAFT (TASK_INVALID_STATE).
	 */
	TaskResponse publish(UUID publicUuid);

	/**
	 * Transition a PUBLISHED task to ARCHIVED. Idempotent failure on
	 * DRAFT (TASK_INVALID_STATE). Already-ARCHIVED also fails.
	 */
	TaskResponse archive(UUID publicUuid);

	void delete(UUID publicUuid);
}
