package com.edushift.modules.academic.course.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/academic/courses/{publicUuid}/levels}.
 *
 * <p>Replace semantics: the list overwrites the current set of
 * associated levels. Sending an empty list is a 400 — the invariant
 * "courses apply to >= 1 level" is enforced at the API edge.</p>
 *
 * <p>Cross-tenant level UUIDs are reported as 404
 * {@code RESOURCE_NOT_FOUND} (anti-enumeration).</p>
 */
public record UpdateCourseLevelsRequest(

		@NotEmpty(message = "levelPublicUuids must contain at least one level")
		List<UUID> levelPublicUuids
) {
}
