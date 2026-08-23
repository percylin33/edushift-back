package com.edushift.modules.tasks.mapper;

import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.TaskSummary;
import com.edushift.modules.tasks.entity.Task;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Task} (Sprint 7a / BE-7a.2).
 *
 * <p>Status, publishedAt and archivedAt added in BUG-2026-07-31-04.
 */
@Component
public class TaskMapper {

	public TaskResponse toResponse(Task entity) {
		return new TaskResponse(
				entity.getPublicUuid(),
				entity.getSection() != null
						? entity.getSection().getPublicUuid() : null,
				entity.getTitle(),
				entity.getDescription(),
				entity.getDueAt(),
				entity.getAttachmentPublicUuid(),
				entity.getOwnerUserId(),
				entity.isAllowResubmission(),
				entity.getStatus(),
				entity.getPublishedAt(),
				entity.getArchivedAt(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	public TaskSummary toSummary(Task entity) {
		return new TaskSummary(
				entity.getPublicUuid(),
				entity.getTitle(),
				entity.getDueAt(),
				entity.getAttachmentPublicUuid() != null,
				entity.getOwnerUserId(),
				entity.getStatus(),
				entity.getCreatedAt());
	}
}
