package com.edushift.modules.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.ai.config.OpenRouterProperties;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QuizQuestionPromptBuilder} (BE-7c.1).
 *
 * <p>Pure unit tests: the builder has no I/O. We verify the prompt
 * structure (system contains the version, few-shot examples and the
 * JSON contract; user contains COUNT/TEMA), the parameter validation
 * (count range, topic non-blank), and the model fallback.</p>
 */
class QuizQuestionPromptBuilderTest {

    private OpenRouterProperties props;
    private QuizQuestionPromptBuilder builder;

    @BeforeEach
    void setUp() {
        props = new OpenRouterProperties();
        props.setDefaultModel("openai/gpt-4o-mini");
        builder = new QuizQuestionPromptBuilder(props);
    }

    @Test
    @DisplayName("build: returns an LlmRequest with system + user + temperature 0.2 + max 2048 tokens")
    void buildReturnsExpectedLlmRequest() {
        LlmRequest req = builder.build("Capitales de Europa", 3, "MC", null);

        assertThat(req.model()).isEqualTo("openai/gpt-4o-mini");
        assertThat(req.temperature()).isEqualTo(0.2);
        assertThat(req.maxTokens()).isEqualTo(2048);

        // System prompt has the contract.
        assertThat(req.systemPrompt())
                .contains("REGLAS DURAS")
                .contains("\"questions\"")
                .contains("MC")
                .contains("TF")
                .contains("SHORT_ANSWER")
                .contains("EJEMPLO 1")
                .contains("EJEMPLO 2");
        // User prompt has the parameters.
        assertThat(req.userPrompt())
                .contains("COUNT: 3")
                .contains("QUESTION_TYPE_FILTER: MC")
                .contains("TEMA: Capitales de Europa");
        // JSON-mode hint.
        assertThat(req.extra()).containsKey("response_format");
    }

    @Test
    @DisplayName("build: null questionType → renders as 'null' in user prompt (model interprets as no filter)")
    void buildNullQuestionType() {
        LlmRequest req = builder.build("Algo", 2, null, null);
        assertThat(req.userPrompt()).contains("QUESTION_TYPE_FILTER: null");
    }

    @Test
    @DisplayName("build: explicit model wins over the default")
    void buildExplicitModelOverridesDefault() {
        LlmRequest req = builder.build("Algo", 1, null, "anthropic/claude-3-haiku");
        assertThat(req.model()).isEqualTo("anthropic/claude-3-haiku");
    }

    @Test
    @DisplayName("build: blank explicit model falls back to props.defaultModel")
    void buildBlankModelFallsBack() {
        LlmRequest req = builder.build("Algo", 1, null, "  ");
        assertThat(req.model()).isEqualTo("openai/gpt-4o-mini");
    }

    @Test
    @DisplayName("build: empty topic is rejected")
    void buildEmptyTopicRejected() {
        assertThatThrownBy(() -> builder.build("", 1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
        assertThatThrownBy(() -> builder.build("   ", 1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("count bounds")
    class CountBounds {
        @Test
        @DisplayName("0 rejected")
        void zeroRejected() {
            assertThatThrownBy(() -> builder.build("X", 0, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("count");
        }
        @Test
        @DisplayName("6 rejected (max 5)")
        void sixRejected() {
            assertThatThrownBy(() -> builder.build("X", 6, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("count");
        }
        @Test
        @DisplayName("negative rejected")
        void negativeRejected() {
            assertThatThrownBy(() -> builder.build("X", -1, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        @Test
        @DisplayName("1..5 accepted")
        void oneToFiveAccepted() {
            for (int c = 1; c <= 5; c++) {
                LlmRequest req = builder.build("X", c, null, null);
                assertThat(req.userPrompt()).contains("COUNT: " + c);
            }
        }
    }

    @Test
    @DisplayName("promptVersion: returns a stable identifier")
    void promptVersionStable() {
        assertThat(builder.promptVersion()).startsWith("quiz-question-suggest/");
    }

    @Test
    @DisplayName("allowedQuestionTypes: returns the 3 valid types")
    void allowedQuestionTypesReturnsTheThree() {
        assertThat(QuizQuestionPromptBuilder.allowedQuestionTypes())
                .containsExactly("MC", "TF", "SHORT_ANSWER");
    }
}
