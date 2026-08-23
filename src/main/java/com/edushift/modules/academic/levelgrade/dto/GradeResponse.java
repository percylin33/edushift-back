package com.edushift.modules.academic.levelgrade.dto;

import com.edushift.modules.academic.levelgrade.entity.TeachingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.levelgrade.entity.Grade}.
 */
public record GradeResponse(
		UUID publicUuid,
		UUID levelPublicUuid,
		String name,
		Integer ordinal,
		TeachingMode teachingMode,
		Instant createdAt,
		Instant updatedAt
) {
}
