package com.edushift.modules.evaluations.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.dto.EvaluationResponse.AssignmentRef;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluationDtoTest {

    @Test
    @DisplayName("CreateEvaluationRequest — constructor + accessors")
    void createRequest() {
        UUID unit = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        var req = new CreateEvaluationRequest(
                EvaluationKind.EXAM, "Midterm", "60 minutes",
                new BigDecimal("25.00"),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 8),
                EvaluationScale.SCORE_0_20,
                unit.toString(), session.toString(), Boolean.TRUE);

        assertThat(req.kind()).isEqualTo(EvaluationKind.EXAM);
        assertThat(req.name()).isEqualTo("Midterm");
        assertThat(req.description()).isEqualTo("60 minutes");
        assertThat(req.weight()).isEqualByComparingTo("25.00");
        assertThat(req.scheduledDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(req.dueDate()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(req.scale()).isEqualTo(EvaluationScale.SCORE_0_20);
        assertThat(req.unitPublicUuid()).isEqualTo(unit.toString());
        assertThat(req.learningSessionPublicUuid()).isEqualTo(session.toString());
        assertThat(req.isActive()).isTrue();
    }

    @Test
    @DisplayName("CreateEvaluationRequest — nulls are tolerated (validation runs in service)")
    void createRequestAllowsNulls() {
        var req = new CreateEvaluationRequest(null, null, null, null,
                null, null, null, null, null, null);
        assertThat(req.kind()).isNull();
        assertThat(req.name()).isNull();
        assertThat(req.weight()).isNull();
    }

    @Test
    @DisplayName("UpdateEvaluationRequest — isEmpty")
    void updateRequestIsEmpty() {
        assertThat(new UpdateEvaluationRequest(null, null, null, null, null, null,
                null, null, null, null).isEmpty()).isTrue();

        var nonEmpty = new UpdateEvaluationRequest(null, "new name", null, null,
                null, null, null, null, null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.name()).isEqualTo("new name");
    }

    @Test
    @DisplayName("EvaluationFilters — accessors")
    void filters() {
        var f = new EvaluationFilters(EvaluationStatus.PUBLISHED, Boolean.TRUE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(f.status()).isEqualTo(EvaluationStatus.PUBLISHED);
        assertThat(f.isActive()).isTrue();
        assertThat(f.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(f.to()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("EvaluationListItem — accessors")
    void listItem() {
        var item = new EvaluationListItem(
                UUID.randomUUID(), EvaluationKind.QUIZ, "Quiz 1",
                new BigDecimal("10.00"),
                LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 22),
                EvaluationScale.LITERAL_A_B_C_D, EvaluationStatus.PUBLISHED,
                18L, Boolean.TRUE,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-15T00:00:00Z"));

        assertThat(item.kind()).isEqualTo(EvaluationKind.QUIZ);
        assertThat(item.name()).isEqualTo("Quiz 1");
        assertThat(item.gradeCount()).isEqualTo(18L);
        assertThat(item.status()).isEqualTo(EvaluationStatus.PUBLISHED);
        assertThat(item.scale()).isEqualTo(EvaluationScale.LITERAL_A_B_C_D);
    }

    @Test
    @DisplayName("EvaluationResponse — full projection")
    void response() {
        UUID puuid = UUID.randomUUID();
        UUID unitUuid = UUID.randomUUID();
        UUID sessionUuid = UUID.randomUUID();
        UUID assignmentUuid = UUID.randomUUID();
        var ref = new AssignmentRef(assignmentUuid, "MATH · 5A · Garcia · B1");

        var resp = new EvaluationResponse(
                puuid, ref, unitUuid, sessionUuid,
                EvaluationKind.RUBRIC, "Oral presentation", "10 minutes",
                new BigDecimal("20.00"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10),
                EvaluationScale.LITERAL_NA, EvaluationStatus.PUBLISHED,
                Instant.parse("2026-06-01T00:00:00Z"), null,
                Boolean.TRUE, 25L,
                Instant.parse("2026-05-15T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(resp.publicUuid()).isEqualTo(puuid);
        assertThat(resp.assignment()).isEqualTo(ref);
        assertThat(resp.assignment().label()).isEqualTo("MATH · 5A · Garcia · B1");
        assertThat(resp.unitPublicUuid()).isEqualTo(unitUuid);
        assertThat(resp.learningSessionPublicUuid()).isEqualTo(sessionUuid);
        assertThat(resp.kind()).isEqualTo(EvaluationKind.RUBRIC);
        assertThat(resp.scale()).isEqualTo(EvaluationScale.LITERAL_NA);
        assertThat(resp.status()).isEqualTo(EvaluationStatus.PUBLISHED);
        assertThat(resp.publishedAt()).isNotNull();
        assertThat(resp.closedAt()).isNull();
        assertThat(resp.gradeCount()).isEqualTo(25L);
    }

    @Test
    @DisplayName("AssignmentRef — accessors")
    void assignmentRef() {
        UUID puuid = UUID.randomUUID();
        var ref = new AssignmentRef(puuid, "label");
        assertThat(ref.publicUuid()).isEqualTo(puuid);
        assertThat(ref.label()).isEqualTo("label");
    }
}