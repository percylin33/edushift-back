package com.edushift.modules.materials.dto;

import com.edushift.modules.files.dto.FileObjectResponse;
import com.edushift.modules.materials.entity.MaterialKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of a material row (Sprint 7a / BE-7a.1).
 *
 * <ul>
 *   <li>{@code file} is non-null when {@code kind=FILE}. The nested
 *       {@link FileObjectResponse} carries the download URL.</li>
 *   <li>{@code externalUrl} is non-null when {@code kind=VIDEO_LINK}.</li>
 *   <li>Cross-field: exactly one of {@code file} / {@code externalUrl}
 *       is non-null. Enforced by the DB CHECK
 *       {@code chk_lms_materials_kind_payload_consistent}.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaterialResponse(
		UUID publicUuid,
		UUID sectionPublicUuid,
		String title,
		String description,
		MaterialKind kind,
		FileObjectResponse file,
		String externalUrl,
		UUID ownerPublicUuid,
		Instant createdAt,
		Instant updatedAt
) {
}
