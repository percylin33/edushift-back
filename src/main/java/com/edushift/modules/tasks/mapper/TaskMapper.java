package com.edushift.modules.tasks.mapper;

import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.TaskSummary;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.teachers.assignments.support.LmsAssignmentProjection;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Task} (Sprint 7a / BE-7a.2).
 *
 * <p>Status, publishedAt and archivedAt added in BUG-2026-07-31-04.
 * Assignment projection fields added in V105.
 * {@code submissionsCount} enriched by {@code TaskServiceImpl}.
 */
@Component
public class TaskMapper {

	public TaskResponse toResponse(Task entity) {
		return toResponse(entity, 0, 0);
	}

	public TaskResponse toResponse(Task entity, int submissionsCount) {
		return toResponse(entity, submissionsCount, 0);
	}

	public TaskResponse toResponse(Task entity, int submissionsCount, int gradedSubmissionsCount) {
		var assignment = entity.getTeacherAssignment();
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
				entity.getMaxScore(),
				entity.getStatus(),
				entity.getPublishedAt(),
				entity.getArchivedAt(),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				LmsAssignmentProjection.publicUuid(assignment),
				LmsAssignmentProjection.coursePublicUuid(assignment),
				LmsAssignmentProjection.courseName(assignment),
				LmsAssignmentProjection.periodLabel(assignment),
				submissionsCount,
				gradedSubmissionsCount);
	}

	public TaskSummary toSummary(Task entity) {
		return toSummary(entity, 0, 0);
	}

	public TaskSummary toSummary(Task entity, int submissionsCount) {
		return toSummary(entity, submissionsCount, 0);
	}

	public TaskSummary toSummary(Task entity, int submissionsCount, int gradedSubmissionsCount) {
		var assignment = entity.getTeacherAssignment();
		return new TaskSummary(
				entity.getPublicUuid(),
				entity.getTitle(),
				entity.getDueAt(),
				entity.getAttachmentPublicUuid() != null,
				entity.getOwnerUserId(),
				entity.getMaxScore(),
				entity.getStatus(),
				entity.getCreatedAt(),
				LmsAssignmentProjection.publicUuid(assignment),
				LmsAssignmentProjection.coursePublicUuid(assignment),
				LmsAssignmentProjection.courseName(assignment),
				LmsAssignmentProjection.periodLabel(assignment),
				submissionsCount,
				gradedSubmissionsCount);
	}
}
