package com.edushift.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for {@code POST /v1/ai/generate-session} (BE-8.1).
 *
 * <p>The teacher picks a topic + course + duration + optional competencies
 * and capacities; the LLM returns a structured JSON lesson outline
 * (activities, resources, evaluation criteria) aligned to the MINEDU
 * Perú template. See {@link GenerateSessionResponse} for the output shape.
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>{@code topic} — 2..200 chars, required.</li>
 *   <li>{@code courseId} — required, must belong to a course in the
 *       caller's tenant (verified by service).</li>
 *   <li>{@code durationMinutes} — 15..240 (4 hours cap).</li>
 *   <li>{@code competencyIds}, {@code capacityIds} — optional, but if
 *       provided each must reference a competency/capacity in the
 *       caller's tenant.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateSessionRequest(

        @NotBlank
        @Size(min = 2, max = 200, message = "topic must be 2..200 chars")
        String topic,

        @NotNull
        UUID courseId,

        @NotNull
        @Min(value = 15, message = "durationMinutes must be at least 15")
        @Max(value = 240, message = "durationMinutes cannot exceed 240")
        Integer durationMinutes,

        List<UUID> competencyIds,

        List<UUID> capacityIds
) {
}
