package com.edushift.modules.evaluations.gradebook.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
        UUID cat = UUID.randomUUID();
        var entry = new GradeBookEvaluationEntry(puuid, "Midterm",
                EvaluationKind.EXAM, EvaluationScale.SCORE_0_20,
                EvaluationStatus.PUBLISHED, new BigDecimal("25.00"),
                LocalDate.of(2026, 5, 1), cat, "Exámenes");
        assertThat(entry.publicUuid()).isEqualTo(puuid);
        assertThat(entry.name()).isEqualTo("Midterm");
        assertThat(entry.kind()).isEqualTo(EvaluationKind.EXAM);
        assertThat(entry.categoryPublicUuid()).isEqualTo(cat);
        assertThat(entry.categoryName()).isEqualTo("Exámenes");
    }

    @Test
    @DisplayName("GradeBookStudentEntry — weighted average present")
    void studentWithAverage() {
        var entry = new GradeBookStudentEntry(UUID.randomUUID(),
                "Jane Doe", new BigDecimal("16.50"), "ACTIVE", Map.of(), null, Map.of());
        assertThat(entry.fullName()).isEqualTo("Jane Doe");
        assertThat(entry.weightedAverage()).isEqualByComparingTo("16.50");
        assertThat(entry.enrollmentStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("GradeBookStudentEntry — null average is representable")
    void studentWithoutAverage() {
        var entry = new GradeBookStudentEntry(UUID.randomUUID(), "Bob", null, "ACTIVE", Map.of(), null, Map.of());
        assertThat(entry.weightedAverage()).isNull();
    }

    @Test
    @DisplayName("GradeBookResponse — full shape with acta header + categories")
    void response() {
        UUID assignment = UUID.randomUUID();
        UUID section = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        UUID period = UUID.randomUUID();
        var resp = new GradeBookResponse(
                assignment, section, "5A",
                UUID.randomUUID(), "5° Primaria", 5, "PRIMARIA",
                course, "Mathematics",
                "Jane Teacher",
                period, PeriodType.BIMESTRE, 1, "Primer bimestre",
                null, null, null,
                List.of(),
                List.of(),
                List.of(),
                List.of());
        assertThat(resp.assignmentPublicUuid()).isEqualTo(assignment);
        assertThat(resp.sectionName()).isEqualTo("5A");
        assertThat(resp.gradeName()).isEqualTo("5° Primaria");
        assertThat(resp.teacherFullName()).isEqualTo("Jane Teacher");
        assertThat(resp.categories()).isEmpty();
        assertThat(resp.students()).isEmpty();
    }
}
