package com.edushift.modules.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.ai.exception.AiParseException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AiDtoTest {

    @Test
    @DisplayName("SuggestQuizQuestionsRequest stores the three fields")
    void suggestRequest() {
        var r = new SuggestQuizQuestionsRequest("Capitales de Europa", 3, "MC");
        assertThat(r.topic()).isEqualTo("Capitales de Europa");
        assertThat(r.count()).isEqualTo(3);
        assertThat(r.questionType()).isEqualTo("MC");
    }

    @Test
    @DisplayName("SuggestQuizQuestionsResponse carries questions + meta")
    void suggestResponse() {
        var q = new QuestionSuggestion(UUID.randomUUID().toString(), "Capital de Francia?",
                "MC", 5,
                List.of(new QuestionSuggestion.OptionSuggestion("Paris", true, "Correct")),
                "Trivia");
        var r = new SuggestQuizQuestionsResponse(List.of(q), "MiniMax/MiniMax-M2",
                "openrouter", "v1", List.of(UUID.randomUUID().toString()));
        assertThat(r.questions()).hasSize(1);
        assertThat(r.model()).isEqualTo("MiniMax/MiniMax-M2");
        assertThat(r.generationUuids()).hasSize(1);
    }

    @Test
    @DisplayName("QuestionSuggestion + nested OptionSuggestion accessors")
    void questionSuggestion() {
        var opt = new QuestionSuggestion.OptionSuggestion("A", true, "explanation");
        var q = new QuestionSuggestion("id-1", "Q?", "MC", 10, List.of(opt), "rationale");
        assertThat(q.id()).isEqualTo("id-1");
        assertThat(q.options()).hasSize(1);
        assertThat(q.options().get(0).isCorrect()).isTrue();
        assertThat(q.options().get(0).explanation()).isEqualTo("explanation");
    }

    @Test
    @DisplayName("GenerateRubricRequest.effectiveLevelCount defaults to 4")
    void rubricRequest() {
        assertThat(new GenerateRubricRequest("R", null, List.of("c1"), null, null)
                .effectiveLevelCount()).isEqualTo(4);
        assertThat(new GenerateRubricRequest("R", null, List.of("c1"), 2, null)
                .effectiveLevelCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("GenerateSessionRequest stores the canonical fields")
    void sessionRequest() {
        var r = new GenerateSessionRequest("Topico", UUID.randomUUID(), UUID.randomUUID(), 60, List.of(), List.of());
        assertThat(r.topic()).isEqualTo("Topico");
        assertThat(r.durationMinutes()).isEqualTo(60);
        assertThat(r.competencyIds()).isEmpty();
    }

    @Test
    @DisplayName("AsyncGenerationAcceptedResponse.forUuid builds a PENDING response with pollUrl")
    void asyncForUuid() {
        UUID id = UUID.randomUUID();
        var r = AsyncGenerationAcceptedResponse.forUuid(id);
        assertThat(r.generationUuid()).isEqualTo(id);
        assertThat(r.status()).isEqualTo("PENDING");
        assertThat(r.pollUrl()).endsWith(id.toString());
    }

    @Test
    @DisplayName("GenerationStatusResponse.fromRow populates status + error fields")
    void statusFromRow() {
        var gen = new com.edushift.modules.ai.entity.AiGeneration();
        gen.setPublicUuid(UUID.randomUUID());
        gen.setStatus(com.edushift.modules.ai.entity.AiGeneration.Status.PROCESSING);
        gen.setErrorCode(null);
        gen.setPromptTokens(10);
        gen.setResponseTokens(20);
        gen.setLatencyMs(150);
        GenerationStatusResponse r = GenerationStatusResponse.fromRow(gen);
        assertThat(r.status()).isEqualTo(com.edushift.modules.ai.entity.AiGeneration.Status.PROCESSING);
        assertThat(r.questions()).isNull(); // only on COMPLETED
        assertThat(r.promptTokens()).isEqualTo(10);
        assertThat(r.errorCode()).isNull();
    }

    @Test
    @DisplayName("GenerationStatusResponse.completed returns COMPLETED + questions")
    void statusCompleted() {
        var gen = new com.edushift.modules.ai.entity.AiGeneration();
        gen.setPublicUuid(UUID.randomUUID());
        gen.setPromptTokens(10);
        gen.setResponseTokens(20);
        gen.setLatencyMs(150);
        gen.setUpdatedAt(java.time.Instant.now());
        var q = List.of(new QuestionSuggestion("id", "Q?", "MC", 5, List.of(), null));
        GenerationStatusResponse r = GenerationStatusResponse.completed(gen, q, "model-x",
                "openrouter", "v1");
        assertThat(r.status()).isEqualTo(com.edushift.modules.ai.entity.AiGeneration.Status.COMPLETED);
        assertThat(r.questions()).hasSize(1);
        assertThat(r.model()).isEqualTo("model-x");
    }

    @Test
    @DisplayName("UsageSummaryResponse carries periods + counters + per-feature / daily lists")
    void usageSummary() {
        var feature = new UsageSummaryResponse.FeatureUsage("QUIZ_QUESTION_SUGGEST", 10, 1000L, 1500L);
        var daily = new UsageSummaryResponse.DailyUsage(java.time.LocalDate.of(2026, 1, 1),
                5, 4, 1, 500L, 800L);
        var r = new UsageSummaryResponse(
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 2, 1),
                100, 1_000_000L, 10, 2500L, 9, 1, List.of(feature), List.of(daily),
                java.time.Instant.now());
        assertThat(r.usedRequests()).isEqualTo(10);
        assertThat(r.byFeature()).hasSize(1);
        assertThat(r.daily()).hasSize(1);
        assertThat(r.byFeature().get(0).feature()).isEqualTo("QUIZ_QUESTION_SUGGEST");
    }

    @Nested
    @DisplayName("GenerateRubricResponse.validate")
    class ValidateRubric {

        @Test
        @DisplayName("rejects when name is too short")
        void shortName() {
            var resp = new GenerateRubricResponse("A", null, List.of(), List.of());
            assertThatThrownBy(() -> resp.validate("raw"))
                    .isInstanceOf(AiParseException.class)
                    .hasMessageContaining("'name' must be 2..160 chars");
        }

        @Test
        @DisplayName("rejects when no criteria or too many criteria")
        void noCriteria() {
            var resp = new GenerateRubricResponse("Valid name", null, null, List.of());
            assertThatThrownBy(() -> resp.validate("raw"))
                    .isInstanceOf(AiParseException.class)
                    .hasMessageContaining("'criteria' must have 1..10 items");

            var tooMany = new GenerateRubricResponse("Valid name", null,
                    java.util.stream.IntStream.range(0, 11)
                            .<com.edushift.modules.ai.dto.GenerateRubricResponse.Criterion>
                            mapToObj(i -> null).toList(),
                    List.of());
            assertThatThrownBy(() -> tooMany.validate("raw"))
                    .isInstanceOf(AiParseException.class);
        }

        @Test
        @DisplayName("rejects when weight sum ≠ 100")
        void weightSum() {
            var c1 = new GenerateRubricResponse.Criterion("k1", "Crit 1", null,
                    new BigDecimal("40.0"),
                    List.of(new GenerateRubricResponse.Descriptor("L1", "d")));
            var c2 = new GenerateRubricResponse.Criterion("k2", "Crit 2", null,
                    new BigDecimal("30.0"),
                    List.of(new GenerateRubricResponse.Descriptor("L1", "d")));
            var levels = List.of(
                    new GenerateRubricResponse.Level("L1", "Level 1", 0),
                    new GenerateRubricResponse.Level("L2", "Level 2", 1));
            var resp = new GenerateRubricResponse("Valid name", null, List.of(c1, c2), levels);
            assertThatThrownBy(() -> resp.validate("raw"))
                    .isInstanceOf(AiParseException.class)
                    .hasMessageContaining("sum of criteria.weight must be 100.0");
        }

        @Test
        @DisplayName("rejects when descriptor references unknown level")
        void unknownLevel() {
            var c1 = new GenerateRubricResponse.Criterion("k1", "Crit 1", null,
                    new BigDecimal("100.0"),
                    List.of(new GenerateRubricResponse.Descriptor("UNKNOWN", "d")));
            var levels = List.of(
                    new GenerateRubricResponse.Level("L1", "Level 1", 0),
                    new GenerateRubricResponse.Level("L2", "Level 2", 1));
            var resp = new GenerateRubricResponse("Valid name", null, List.of(c1), levels);
            assertThatThrownBy(() -> resp.validate("raw"))
                    .isInstanceOf(AiParseException.class)
                    .hasMessageContaining("does not match any defined level");
        }

        @Test
        @DisplayName("accepts a well-formed rubric")
        void valid() {
            var c1 = new GenerateRubricResponse.Criterion("k1", "Crit 1", null,
                    new BigDecimal("60.0"),
                    List.of(new GenerateRubricResponse.Descriptor("L1", "d"),
                            new GenerateRubricResponse.Descriptor("L2", "d")));
            var c2 = new GenerateRubricResponse.Criterion("k2", "Crit 2", null,
                    new BigDecimal("40.0"),
                    List.of(new GenerateRubricResponse.Descriptor("L1", "d"),
                            new GenerateRubricResponse.Descriptor("L2", "d")));
            var levels = List.of(
                    new GenerateRubricResponse.Level("L1", "Level 1", 0),
                    new GenerateRubricResponse.Level("L2", "Level 2", 1));
            var resp = new GenerateRubricResponse("Valid name", null, List.of(c1, c2), levels);
            // Should not throw
            resp.validate("raw text");
        }
    }
}