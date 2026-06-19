package com.edushift.modules.files.dto;

import com.edushift.modules.files.entity.FileObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a {@link FileObject} row (Sprint 7a / BE-7a.0).
 *
 * <p>The {@code url} field is the path the FE can hit to download the
 * bytes ({@code GET /v1/files/{publicUuid}/download}). For the
 * Firebase provider with {@code serve-from-controller=false} it is a
 * pre-signed external URL with a TTL (default 15 min); otherwise it
 * is the same controller path.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileObjectResponse(
		UUID publicUuid,
		String originalName,
		String contentType,
		long sizeBytes,
		String checksumSha256,
		String url,
		Instant createdAt
) {

	public static FileObjectResponse fromEntity(FileObject entity, String url) {
		return new FileObjectResponse(
				entity.getPublicUuid(),
				entity.getOriginalName(),
				entity.getContentType(),
				entity.getSizeBytes(),
				entity.getChecksumSha256(),
				url,
				entity.getCreatedAt());
	}
}
