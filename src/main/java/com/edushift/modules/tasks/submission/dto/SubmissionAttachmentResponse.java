package com.edushift.modules.tasks.submission.dto;

import com.edushift.modules.files.entity.FileObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * File metadata embedded in submission read projections (BE-7a.2).
 *
 * <p>Matches the FE {@code SubmissionAttachmentRaw} contract:
 * filename + downloadUrl + sizeBytes.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmissionAttachmentResponse(
		UUID publicUuid,
		String filename,
		long sizeBytes,
		String contentType,
		String downloadUrl
) {

	public static SubmissionAttachmentResponse fromFileObject(FileObject entity) {
		String downloadUrl = String.format("/api/v1/files/%s/download", entity.getPublicUuid());
		return new SubmissionAttachmentResponse(
				entity.getPublicUuid(),
				entity.getOriginalName(),
				entity.getSizeBytes(),
				entity.getContentType(),
				downloadUrl);
	}
}
