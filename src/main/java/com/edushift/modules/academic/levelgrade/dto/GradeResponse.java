package com.edushift.modules.academic.levelgrade.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.levelgrade.entity.Grade}.
 *
 * <p>{@code levelPublicUuid} is included so a grade response is
 * self-contained: the FE doesn't need to remember the parent level.</p>
 */
public record GradeResponse(
		UUID publicUuid,
		UUID levelPublicUuid,
		String name,
		Integer ordinal,
		Instant createdAt,
		Instant updatedAt
) {
}
