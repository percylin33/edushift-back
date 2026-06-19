package com.edushift.modules.tasks.submission.dto;

import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of a {@link com.edushift.modules.tasks.submission.entity.Submission}.
 *
 * <p>For PARENT-on-behalf, {@code studentPublicUuid} is the child
 * (recipient of the grade) and {@code submitterPublicUuid} is the
 * parent (who pressed submit).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmissionResponse(
		UUID publicUuid,
		UUID taskPublicUuid,
		UUID studentPublicUuid,
		UUID submitterPublicUuid,
		String textBody,
		UUID attachmentPublicUuid,
		SubmissionStatus status,
		Integer grade,
		String feedback,
		UUID gradedByPublicUuid,
		Instant gradedAt,
		Boolean wasIdempotent,
		Instant createdAt,
		Instant updatedAt
) {
}
