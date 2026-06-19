package com.edushift.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for {@code POST /v1/ai/generate-rubric} (BE-8.2).
 *
 * <p>The teacher provides:</p>
 * <ul>
 *   <li>{@code name} — the rubric name (used as context for the LLM,
 *       not necessarily the final persisted name).</li>
 *   <li>{@code criteria} — 1..10 criterion names the teacher wants
 *       evaluated. The LLM expands each with descriptions, weights
 *       and per-level descriptors.</li>
 *   <li>{@code levelCount} — 2..4 achievement levels (default 4 = the
 *       canonical MINEDU LITERAL set).</li>
 *   <li>{@code seedRubricId} — optional. If provided, the LLM uses
 *       the existing rubric's name + criterion shape as a starting
 *       point (ADR-8.3: "fork opcional"). Cross-tenant lookups
 *       return 404.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateRubricRequest(

        @NotBlank
        @Size(min = 2, max = 160, message = "name must be 2..160 chars")
        String name,

        @Size(max = 2000)
        String description,

        @NotEmpty
        @Size(min = 1, max = 10, message = "criteria must have 1..10 items")
        List<@NotBlank @Size(min = 2, max = 160) String> criteria,

        @Min(value = 2, message = "levelCount must be at least 2")
        @Max(value = 4, message = "levelCount cannot exceed 4")
        Integer levelCount,

        UUID seedRubricId
) {
    /**
     * Defaults {@code levelCount} to 4 (canonical MINEDU: EN_INICIO,
     * EN_PROCESO, LOGRO_ESPERADO, LOGRO_DESTACADO) when the caller
     * omits it. Centralised here so the validation message matches
     * the actual default.
     */
    public int effectiveLevelCount() {
        return levelCount == null ? 4 : levelCount;
    }
}
