package com.edushift.modules.tasks.submission.mapper;

import com.edushift.modules.tasks.submission.dto.SubmissionResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionSummary;
import com.edushift.modules.tasks.submission.entity.Submission;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Submission} (Sprint 7a / BE-7a.2).
 */
@Component
public class SubmissionMapper {

	public SubmissionResponse toResponse(Submission entity) {
		return toResponse(entity, null);
	}

	public SubmissionResponse toResponse(Submission entity, Boolean wasIdempotent) {
		return new SubmissionResponse(
				entity.getPublicUuid(),
				entity.getTask() != null
						? entity.getTask().getPublicUuid() : null,
				entity.getStudentUserId(),
				entity.getSubmitterUserId(),
				entity.getTextBody(),
				entity.getAttachmentPublicUuid(),
				entity.getStatus(),
				entity.getGrade() != null ? entity.getGrade().intValue() : null,
				entity.getFeedback(),
				entity.getGradedByUserId(),
				entity.getGradedAt(),
				wasIdempotent,
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	public SubmissionSummary toSummary(Submission entity) {
		return new SubmissionSummary(
				entity.getPublicUuid(),
				entity.getStudentUserId(),
				entity.getStatus(),
				entity.getGrade() != null ? entity.getGrade().intValue() : null,
				entity.getAttachmentPublicUuid() != null,
				entity.getCreatedAt());
	}
}
