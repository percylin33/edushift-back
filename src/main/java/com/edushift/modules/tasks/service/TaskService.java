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
 */
public interface TaskService {

	TaskResponse create(UUID sectionPublicUuid, CreateTaskRequest request, UUID ownerUserId);

	Page<TaskSummary> listBySection(UUID sectionPublicUuid, Pageable pageable);

	TaskResponse getByPublicUuid(UUID publicUuid);

	TaskResponse patch(UUID publicUuid, UpdateTaskRequest request);

	void delete(UUID publicUuid);
}
