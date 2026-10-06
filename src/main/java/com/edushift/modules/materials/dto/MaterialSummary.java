package com.edushift.modules.materials.dto;

import com.edushift.modules.materials.entity.MaterialKind;
import java.time.Instant;
import java.util.UUID;

/**
 * Lean projection for list endpoints ({@code GET /sections/{uuid}/materials}).
 */
public record MaterialSummary(
		UUID publicUuid,
		String title,
		MaterialKind kind,
		Long fileSizeBytes,
		UUID ownerPublicUuid,
		Instant createdAt,
		UUID teacherAssignmentPublicUuid,
		UUID coursePublicUuid,
		String courseName,
		String periodLabel,
		UUID filePublicUuid,
		String filename,
		String contentType,
		String externalUrl
) {
}
