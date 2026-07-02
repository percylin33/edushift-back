package com.edushift.modules.evaluations.gradebook.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GradeBookDtoTest {

    @Test
    @DisplayName("GradeBookCellEntry — score path")
    void cellScore() {
        UUID student = UUID.randomUUID();
        UUID evaluation = UUID.randomUUID();
        Instant t = Instant.now();
        var cell = new GradeBookCellEntry(student, evaluation,
                new BigDecimal("14.50"), null, t);
        assertThat(cell.studentPublicUuid()).isEqualTo(student);
        assertThat(cell.evaluationPublicUuid()).isEqualTo(evaluation);
        assertThat(cell.score()).isEqualByComparingTo("14.50");
        assertThat(cell.literal()).isNull();
        assertThat(cell.recordedAt()).isEqualTo(t);
    }

    @Test
    @DisplayName("GradeBookCellEntry — literal path")
    void cellLiteral() {
        var cell = new GradeBookCellEntry(UUID.randomUUID(), UUID.randomUUID(),
                null, "A", Instant.now());
        assertThat(cell.literal()).isEqualTo("A");
        assertThat(cell.score()).isNull();
    }

    @Test
    @DisplayName("GradeBookEvaluationEntry — accessors")
    void evaluationEntry() {
        UUID puuid = UUID.randomUUID();
        var entry = new GradeBookEvaluationEntry(puuid, "Midterm",
                EvaluationKind.EXAM, EvaluationScale.SCORE_0_20,
                EvaluationStatus.PUBLISHED, new BigDecimal("25.00"),
                LocalDate.of(2026, 5, 1));
        assertThat(entry.publicUuid()).isEqualTo(puuid);
        assertThat(entry.name()).isEqualTo("Midterm");
        assertThat(entry.kind()).isEqualTo(EvaluationKind.EXAM);
        assertThat(entry.scale()).isEqualTo(EvaluationScale.SCORE_0_20);
        assertThat(entry.status()).isEqualTo(EvaluationStatus.PUBLISHED);
        assertThat(entry.weight()).isEqualByComparingTo("25.00");
        assertThat(entry.scheduledDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("GradeBookStudentEntry — weighted average present")
    void studentWithAverage() {
        var entry = new GradeBookStudentEntry(UUID.randomUUID(),
                "Jane Doe", new BigDecimal("16.50"));
        assertThat(entry.fullName()).isEqualTo("Jane Doe");
        assertThat(entry.weightedAverage()).isEqualByComparingTo("16.50");
    }

    @Test
    @DisplayName("GradeBookStudentEntry — null average is representable")
    void studentWithoutAverage() {
        var entry = new GradeBookStudentEntry(UUID.randomUUID(), "Bob", null);
        assertThat(entry.weightedAverage()).isNull();
    }

    @Test
    @DisplayName("GradeBookResponse — full shape with three lists")
    void response() {
        UUID assignment = UUID.randomUUID();
        UUID section = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        var resp = new GradeBookResponse(
                assignment, section, "5A", course, "Mathematics",
                List.of(),
                List.of(),
                List.of());
        assertThat(resp.assignmentPublicUuid()).isEqualTo(assignment);
        assertThat(resp.sectionPublicUuid()).isEqualTo(section);
        assertThat(resp.sectionName()).isEqualTo("5A");
        assertThat(resp.coursePublicUuid()).isEqualTo(course);
        assertThat(resp.courseName()).isEqualTo("Mathematics");
        assertThat(resp.students()).isEmpty();
        assertThat(resp.evaluations()).isEmpty();
        assertThat(resp.cells()).isEmpty();
    }
}