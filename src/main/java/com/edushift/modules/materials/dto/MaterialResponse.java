package com.edushift.modules.materials.dto;

import com.edushift.modules.files.dto.FileObjectResponse;
import com.edushift.modules.materials.entity.MaterialKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of a material row (Sprint 7a / BE-7a.1 + V105).
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
		Instant updatedAt,
		UUID teacherAssignmentPublicUuid,
		UUID coursePublicUuid,
		String courseName,
		String periodLabel
) {
}
