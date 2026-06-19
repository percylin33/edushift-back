package com.edushift.modules.ai.dto;

import com.edushift.modules.ai.entity.AiGeneration;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/lms/ai/generations/{publicUuid}}
 * (BE-7c.2).
 *
 * <p>Designed to be polled by the FE (FE-7c.3) at ~1-2s intervals
 * after submitting an async generation. The shape is intentionally
 * <em>flat</em> so the FE can render the current state without
 * branching on union types:</p>
 * <ul>
 *   <li>{@link #status} — one of {@code PENDING} / {@code PROCESSING}
 *       / {@code COMPLETED} / {@code FAILED} / {@code CANCELLED}.</li>
 *   <li>{@link #questions}, {@link #model}, {@link #provider} —
 *       populated only on {@code COMPLETED}.</li>
 *   <li>{@link #errorCode}, {@link #errorMessage} — populated only on
 *       {@code FAILED}.</li>
 *   <li>{@link #createdAt} / {@link #completedAt} / {@link #latencyMs}
 *       — bookkeeping for the audit timeline.</li>
 * </ul>
 *
 * <p>Null fields are omitted from the JSON via
 * {@link JsonInclude.Include#NON_NULL} so the wire stays compact on
 * the common {@code PENDING} / {@code PROCESSING} poll responses.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerationStatusResponse(
        UUID generationUuid,
        AiGeneration.Status status,
        List<QuestionSuggestion> questions,
        String model,
        String provider,
        String promptVersion,
        String errorCode,
        String errorMessage,
        Integer promptTokens,
        Integer responseTokens,
        Integer latencyMs,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    /**
     * Convenience factory for a {@code PENDING} / {@code PROCESSING}
     * response, when we only have the row's lifecycle metadata.
     */
    public static GenerationStatusResponse fromRow(AiGeneration gen) {
        return new GenerationStatusResponse(
                gen.getPublicUuid(),
                gen.getStatus(),
                null,           // questions — only on COMPLETED
                null,           // model
                null,           // provider
                null,           // promptVersion
                gen.getErrorCode(),
                gen.getErrorMessage(),
                gen.getPromptTokens(),
                gen.getResponseTokens(),
                gen.getLatencyMs(),
                gen.getCreatedAt(),
                gen.getUpdatedAt(),
                null            // completedAt — not yet captured on the entity
        );
    }

    /**
     * Convenience factory for a {@code COMPLETED} response that
     * includes the suggestions, model, and provider id.
     */
    public static GenerationStatusResponse completed(AiGeneration gen,
                                                     List<QuestionSuggestion> questions,
                                                     String model,
                                                     String provider,
                                                     String promptVersion) {
        return new GenerationStatusResponse(
                gen.getPublicUuid(),
                AiGeneration.Status.COMPLETED,
                questions,
                model,
                provider,
                promptVersion,
                null,
                null,
                gen.getPromptTokens(),
                gen.getResponseTokens(),
                gen.getLatencyMs(),
                gen.getCreatedAt(),
                gen.getUpdatedAt(),
                gen.getUpdatedAt() // approximated from updatedAt
        );
    }
}
