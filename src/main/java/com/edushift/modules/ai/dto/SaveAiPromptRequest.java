package com.edushift.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating an {@code AiPrompt} row
 * (Sprint 18 / BE-18.5).
 *
 * <p>Both the system prompt and the user-prompt template are
 * required — leaving either blank would produce a broken LLM
 * call. The template key is a free-form identifier
 * ({@code session-generator}, {@code rubric-generator},
 * {@code quiz-question-generator}, etc.) so we restrict it to a
 * safe slug charset to avoid path-traversal-style shenanigans
 * in the admin REST URL.</p>
 *
 * @param templateKey         stable identifier per LLM use case
 * @param version             semver-ish label (bumped on every text edit)
 * @param description         optional human-readable description
 * @param systemPrompt        the LLM system message
 * @param userPromptTemplate  template with named placeholders
 * @param activate            when true, this row becomes the ACTIVE
 *                            version for {@code templateKey}; the
 *                            previous active row (if any) is atomically
 *                            flipped to {@code active=false}
 */
public record SaveAiPromptRequest(

		@NotBlank
		@Size(max = 64)
		@Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$",
				message = "templateKey must be lowercase kebab-case, 2..64 chars")
		String templateKey,

		@NotBlank
		@Size(max = 16)
		@Pattern(regexp = "^[A-Za-z0-9._+-]{1,16}$",
				message = "version must be a short semver-ish label (e.g. v1, v1.1, 2025-04-01)")
		String version,

		@Size(max = 500)
		String description,

		@NotBlank
		@Size(max = 32000)
		String systemPrompt,

		@NotBlank
		@Size(max = 32000)
		String userPromptTemplate,

		Boolean activate
) {
}
