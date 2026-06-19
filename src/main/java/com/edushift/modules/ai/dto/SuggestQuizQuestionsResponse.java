package com.edushift.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response body for {@code POST /api/v1/lms/ai/quiz-questions} (BE-7c.1).
 *
 * <p>Wraps the LLM suggestions plus a few fields the FE panel
 * (FE-7b.4 / {@code AiAssistantPanelComponent}) finds useful for
 * rendering the meta-UI: which model was used, the prompt version,
 * and the IDs of the {@code ai_generations} rows that backed this
 * call (for the "Regenerar" audit trail).</p>
 *
 * @param questions       the suggestions. Always non-null; empty list is
 *                        possible (LLM returned no valid output but did
 *                        not fail outright).
 * @param model           the model id that produced the suggestions.
 * @param provider        the {@link com.edushift.modules.ai.llm.LlmClient#providerId()}.
 * @param promptVersion   the prompt template version (see
 *                        {@link com.edushift.modules.ai.prompt.QuizQuestionPromptBuilder#PROMPT_VERSION}).
 * @param generationUuids the {@code ai_generations.public_uuid} of the
 *                        records persisted for this call. Currently
 *                        always a single UUID; the field is a list for
 *                        future-proofing (BE-7c.2 may chunk a single
 *                        user request into N LLM calls).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuggestQuizQuestionsResponse(
        List<QuestionSuggestion> questions,
        String model,
        String provider,
        String promptVersion,
        List<String> generationUuids
) {
}
