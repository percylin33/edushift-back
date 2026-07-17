package com.edushift.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for an {@code AiPrompt} row (Sprint 18 / BE-18.5).
 *
 * <p>Mirrors the entity's public fields; we never expose the
 * internal primary key. The {@code isActive} field is renamed
 * {@code active} in the JSON payload because the BE contract
 * follows the {@code @Column} name and the admin UI tests for that.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiPromptResponse(
		UUID publicUuid,
		String templateKey,
		String version,
		String description,
		String systemPrompt,
		String userPromptTemplate,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {
}
