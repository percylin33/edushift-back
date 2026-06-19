package com.edushift.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A single question suggestion produced by the AI assistant (BE-7c.1).
 *
 * <p>This is the wire shape that the {@code AiController} returns to the
 * FE and the shape that the {@code LmsAiService} extracts from the LLM
 * JSON. The {@code id} field is generated server-side (UUID v7) so the
 * FE can use it as a stable key for the
 * Aceptar/Regenerar/Descartar flow.</p>
 *
 * <p>The {@code questionType} is a literal string (matches the
 * {@code com.edushift.modules.quizzes.entity.QuestionType} wire values
 * defined in Sprint 7b). The LLM is instructed to emit one of
 * {@code "MC" | "TF" | "SHORT_ANSWER"}; the parser rejects anything else
 * and the LLM call is logged as FAILED.</p>
 *
 * <p>This DTO is also accepted (in a slightly different shape, with
 * {@code aiRationale}) by the FE's
 * {@code AiAssistantPanelComponent} (FE-7b.4), so the wire contract is
 * symmetric.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionSuggestion(
        String id,
        String prompt,
        String questionType,
        int points,
        List<OptionSuggestion> options,
        String rationale
) {
    /**
     * Single option of a {@link QuestionSuggestion}. Mirrors the
     * {@code lms_quiz_options} wire shape (label, isCorrect, explanation)
     * but without the database-only fields (position, id, publicUuid).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OptionSuggestion(
            String label,
            boolean isCorrect,
            String explanation
    ) {}
}
