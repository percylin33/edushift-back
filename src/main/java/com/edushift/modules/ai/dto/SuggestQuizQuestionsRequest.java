package com.edushift.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/lms/ai/quiz-questions} (BE-7c.1).
 *
 * <p>Mirrors the FE's {@code AiAssistantRequest}
 * ({@code edushift-front/src/app/features/lms/quiz/models/ai-assistant.model.ts})
 * 1:1 so the contract is symmetric.</p>
 *
 * @param topic        free-form topic (e.g. "Capitales de Europa"). 2..200 chars.
 * @param count        1..5; how many questions the model should produce.
 * @param questionType optional filter, one of {@code MC | TF | SHORT_ANSWER}.
 *                     {@code null} or empty = any type allowed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuggestQuizQuestionsRequest(
        @NotBlank(message = "topic is required")
        @Size(min = 2, max = 200, message = "topic must be 2..200 chars")
        String topic,

        @Min(value = 1, message = "count must be >= 1")
        @Max(value = 5, message = "count must be <= 5")
        int count,

        @Pattern(regexp = "MC|TF|SHORT_ANSWER",
                 message = "questionType must be one of MC, TF, SHORT_ANSWER")
        String questionType
) {
}
