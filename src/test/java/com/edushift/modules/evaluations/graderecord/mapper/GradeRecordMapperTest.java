package com.edushift.modules.evaluations.graderecord.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.Student;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GradeRecordMapperTest {

    private final GradeRecordMapper mapper = new GradeRecordMapper();

    private GradeRecord grade;
    private Evaluation evaluation;
    private Student student;

    @BeforeEach
    void setUp() throws Exception {
        evaluation = new Evaluation();
        setField(evaluation, "publicUuid", UUID.randomUUID());
        evaluation.setName("Midterm");
        evaluation.setScale(EvaluationScale.SCORE_0_20);
        evaluation.setStatus(EvaluationStatus.PUBLISHED);

        student = new Student();
        setField(student, "publicUuid", UUID.randomUUID());
        student.setDocumentType(DocumentType.DNI);
        student.setDocumentNumber("12345678");
        student.setFirstName("Jane");
        student.setLastName("Doe");
        student.setSecondLastName("Smith");
        student.setGender(Gender.FEMALE);

        grade = new GradeRecord();
        setField(grade, "publicUuid", UUID.randomUUID());
        setField(grade, "id", UUID.randomUUID());
        grade.setEvaluation(evaluation);
        grade.setStudent(student);
        grade.setScore(new BigDecimal("14.50"));
        grade.setLiteral(null);
        grade.setComments("Good work");
        grade.setRecordedAt(Instant.parse("2026-05-01T00:00:00Z"));
        grade.setRecordedByUserId(UUID.randomUUID());
        grade.setIsActive(Boolean.TRUE);
        setField(grade, "createdAt", Instant.parse("2026-05-01T00:00:00Z"));
        setField(grade, "updatedAt", Instant.parse("2026-05-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields including nested refs")
        void mapsAllFields() {
            var resp = mapper.toResponse(grade);

            assertThat(resp.publicUuid()).isEqualTo(grade.getPublicUuid());
            assertThat(resp.score()).isEqualByComparingTo("14.50");
            assertThat(resp.literal()).isNull();
            assertThat(resp.comments()).isEqualTo("Good work");
            assertThat(resp.recordedAt()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
            assertThat(resp.recordedByUserId()).isEqualTo(grade.getRecordedByUserId());
            assertThat(resp.isActive()).isTrue();

            assertThat(resp.evaluation().publicUuid()).isEqualTo(evaluation.getPublicUuid());
            assertThat(resp.evaluation().name()).isEqualTo("Midterm");
            assertThat(resp.evaluation().scale()).isEqualTo(EvaluationScale.SCORE_0_20);
            assertThat(resp.evaluation().status()).isEqualTo(EvaluationStatus.PUBLISHED);

            assertThat(resp.student().publicUuid()).isEqualTo(student.getPublicUuid());
            assertThat(resp.student().firstName()).isEqualTo("Jane");
            assertThat(resp.student().lastName()).isEqualTo("Doe");
            assertThat(resp.student().secondLastName()).isEqualTo("Smith");
        }

        @Test
        @DisplayName("literal path — score null, literal set")
        void literalPath() {
            grade.setScore(null);
            grade.setLiteral("A");
            var resp = mapper.toResponse(grade);
            assertThat(resp.literal()).isEqualTo("A");
            assertThat(resp.score()).isNull();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps without nested evaluation ref")
        void mapsFields() {
            var item = mapper.toListItem(grade);

            assertThat(item.publicUuid()).isEqualTo(grade.getPublicUuid());
            assertThat(item.studentPublicUuid()).isEqualTo(student.getPublicUuid());
            assertThat(item.studentFirstName()).isEqualTo("Jane");
            assertThat(item.studentLastName()).isEqualTo("Doe");
            assertThat(item.studentSecondLastName()).isEqualTo("Smith");
            assertThat(item.score()).isEqualByComparingTo("14.50");
            assertThat(item.comments()).isEqualTo("Good work");
            assertThat(item.isActive()).isTrue();
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            }
            catch (NoSuchFieldException ignore) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}