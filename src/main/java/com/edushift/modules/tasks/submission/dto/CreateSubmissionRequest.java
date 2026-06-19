package com.edushift.modules.tasks.submission.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * JSON body for {@code POST /tasks/{uuid}/submissions}
 * (Sprint 7a / BE-7a.2).
 *
 * <p>{@code studentPublicUuid} identifies the target student — the
 * recipient of the grade. For STUDENT self-submit, the body is the
 * student's own public UUID. For PARENT on-behalf, the body is the
 * child's public UUID (the bearer is the parent, see
 * {@code submitterPublicUuid} of the response).
 */
public record CreateSubmissionRequest(
		@NotNull UUID studentPublicUuid,
		@Size(max = 50000) String textBody,
		UUID attachmentPublicUuid
) {
}
