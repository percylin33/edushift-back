package com.edushift.modules.evaluations.gradebook;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.gradebook.GradeAverageCalculator.GradeKey;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCategoryEntry;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GradeAverageCalculatorTest {

    @Test
    void flatWeightsMatchTheGradeBookFormula() {
        UUID studentId = UUID.randomUUID();
        Evaluation tarea = eval(EvaluationScale.SCORE_0_20, new BigDecimal("1"), null);
        Evaluation examen = eval(EvaluationScale.SCORE_0_20, new BigDecimal("3"), null);
        Map<GradeKey, GradeRecord> grades = grades(
                grade(tarea, studentId, new BigDecimal("20"), null),
                grade(examen, studentId, new BigDecimal("12"), null));

        GradeAverageCalculator.Row row = GradeAverageCalculator.forStudent(
                studentId, List.of(tarea, examen), grades, List.of(), false);

        // (20*1 + 12*3) / 4 = 14.00
        assertThat(row.weightedAverage()).isEqualByComparingTo("14.00");
        assertThat(row.literalAverage()).isNull();
    }

    @Test
    void categoryWeightsMatchTheGradeBookFormula() {
        UUID studentId = UUID.randomUUID();
        GradeCategory practicas = category();
        GradeCategory examen = category();
        Evaluation p = eval(EvaluationScale.SCORE_0_20, BigDecimal.ONE, practicas);
        Evaluation e = eval(EvaluationScale.SCORE_0_20, BigDecimal.ONE, examen);
        List<GradeBookCategoryEntry> categories = List.of(
                new GradeBookCategoryEntry(practicas.getPublicUuid(), "Prácticas", null, 1,
                        new BigDecimal("40"), true, true),
                new GradeBookCategoryEntry(examen.getPublicUuid(), "Examen", null, 2,
                        new BigDecimal("60"), true, true));

        GradeAverageCalculator.Row row = GradeAverageCalculator.forStudent(
                studentId,
                List.of(p, e),
                grades(
                        grade(p, studentId, new BigDecimal("10"), null),
                        grade(e, studentId, new BigDecimal("20"), null)),
                categories,
                false);

        // category avgs 10 and 20; (10*40 + 20*60) / 100 = 16.00
        assertThat(row.weightedAverage()).isEqualByComparingTo("16.00");
        assertThat(row.categoryAverages().get(practicas.getPublicUuid())).isEqualByComparingTo("10.00");
        assertThat(row.categoryAverages().get(examen.getPublicUuid())).isEqualByComparingTo("20.00");
    }

    @Test
    void cnebSheetReturnsALetterAndNoNumericAverage() {
        UUID studentId = UUID.randomUUID();
        Evaluation eval = eval(EvaluationScale.LITERAL_CNEB, BigDecimal.ONE, null);
        GradeAverageCalculator.Row row = GradeAverageCalculator.forStudent(
                studentId,
                List.of(eval),
                grades(grade(eval, studentId, null, "AD")),
                List.of(),
                true);

        assertThat(row.literalAverage()).isEqualTo("AD");
        assertThat(row.weightedAverage()).isNull();
    }

    @Test
    void anotherStudentsGradeDoesNotMoveTheAverage() {
        UUID studentId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Evaluation eval = eval(EvaluationScale.SCORE_0_20, BigDecimal.ONE, null);
        GradeAverageCalculator.Row row = GradeAverageCalculator.forStudent(
                studentId,
                List.of(eval),
                grades(grade(eval, otherId, new BigDecimal("20"), null)),
                List.of(),
                false);

        assertThat(row.weightedAverage()).isNull();
    }

    private static Evaluation eval(EvaluationScale scale, BigDecimal weight, GradeCategory category) {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(UUID.randomUUID());
        evaluation.setPublicUuid(UUID.randomUUID());
        evaluation.setName("Eval");
        evaluation.setKind(EvaluationKind.TASK);
        evaluation.setScale(scale);
        evaluation.setStatus(EvaluationStatus.PUBLISHED);
        evaluation.setWeight(weight);
        evaluation.setScheduledDate(LocalDate.now());
        evaluation.setCategory(category);
        return evaluation;
    }

    private static GradeCategory category() {
        GradeCategory category = new GradeCategory();
        category.setPublicUuid(UUID.randomUUID());
        return category;
    }

    private static GradeRecord grade(Evaluation evaluation, UUID studentId, BigDecimal score, String literal) {
        GradeRecord record = new GradeRecord();
        record.setEvaluation(evaluation);
        record.setScore(score);
        record.setLiteral(literal);
        record.setStudent(studentWithId(studentId));
        return record;
    }

    private static com.edushift.modules.students.entity.Student studentWithId(UUID id) {
        com.edushift.modules.students.entity.Student student =
                new com.edushift.modules.students.entity.Student();
        student.setId(id);
        return student;
    }

    private static Map<GradeKey, GradeRecord> grades(GradeRecord... records) {
        Map<GradeKey, GradeRecord> map = new HashMap<>();
        for (GradeRecord record : records) {
            map.put(new GradeKey(record.getEvaluation().getId(), record.getStudent().getId()), record);
        }
        return map;
    }
}
