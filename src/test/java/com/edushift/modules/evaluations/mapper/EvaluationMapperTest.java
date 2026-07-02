package com.edushift.modules.evaluations.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.evaluations.dto.CreateEvaluationRequest;
import com.edushift.modules.evaluations.dto.EvaluationResponse;
import com.edushift.modules.evaluations.dto.UpdateEvaluationRequest;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EvaluationMapperTest {

    private final EvaluationMapper mapper = new EvaluationMapper();

    private Evaluation evaluation;
    private TeacherAssignment assignment;
    private Course course;
    private Section section;
    private Teacher teacher;
    private AcademicPeriod period;

    @BeforeEach
    void setUp() throws Exception {
        course = newCourse("MAT", "Mathematics");
        section = newSection("5A");
        teacher = newTeacher("Garcia", "Lopez");
        period = newPeriod(PeriodType.BIMESTRE, 1, "Bimestre I");
        assignment = newAssignment(course, section, teacher, period);

        evaluation = new Evaluation();
        setField(evaluation, "publicUuid", UUID.randomUUID());
        setField(evaluation, "id", UUID.randomUUID());
        evaluation.setTeacherAssignment(assignment);
        evaluation.setKind(EvaluationKind.EXAM);
        evaluation.setName("Midterm");
        evaluation.setDescription("60 minutes");
        evaluation.setWeight(new BigDecimal("25.00"));
        evaluation.setScheduledDate(LocalDate.of(2026, 5, 1));
        evaluation.setDueDate(LocalDate.of(2026, 5, 8));
        evaluation.setScale(EvaluationScale.SCORE_0_20);
        evaluation.setStatus(EvaluationStatus.PUBLISHED);
        evaluation.setPublishedAt(Instant.parse("2026-05-01T00:00:00Z"));
        evaluation.setIsActive(Boolean.TRUE);
        setField(evaluation, "createdAt", Instant.parse("2026-04-01T00:00:00Z"));
        setField(evaluation, "updatedAt", Instant.parse("2026-05-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields")
        void mapsAllFields() {
            EvaluationResponse resp = mapper.toResponse(evaluation, 18L);

            assertThat(resp.publicUuid()).isEqualTo(evaluation.getPublicUuid());
            assertThat(resp.kind()).isEqualTo(EvaluationKind.EXAM);
            assertThat(resp.name()).isEqualTo("Midterm");
            assertThat(resp.description()).isEqualTo("60 minutes");
            assertThat(resp.weight()).isEqualByComparingTo("25.00");
            assertThat(resp.scheduledDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(resp.dueDate()).isEqualTo(LocalDate.of(2026, 5, 8));
            assertThat(resp.scale()).isEqualTo(EvaluationScale.SCORE_0_20);
            assertThat(resp.status()).isEqualTo(EvaluationStatus.PUBLISHED);
            assertThat(resp.publishedAt()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
            assertThat(resp.closedAt()).isNull();
            assertThat(resp.isActive()).isTrue();
            assertThat(resp.gradeCount()).isEqualTo(18L);
        }

        @Test
        @DisplayName("null anchors are representable")
        void nullAnchors() {
            EvaluationResponse resp = mapper.toResponse(evaluation, 0L);
            assertThat(resp.unitPublicUuid()).isNull();
            assertThat(resp.learningSessionPublicUuid()).isNull();
        }

        @Test
        @DisplayName("non-null anchors are projected")
        void nonNullAnchors() throws Exception {
            UUID unitUuid = UUID.randomUUID();
            UUID sessionUuid = UUID.randomUUID();
            Unit unit = new Unit();
            setField(unit, "publicUuid", unitUuid);
            LearningSession session = new LearningSession();
            setField(session, "publicUuid", sessionUuid);
            evaluation.setUnit(unit);
            evaluation.setLearningSession(session);

            EvaluationResponse resp = mapper.toResponse(evaluation, 0L);

            assertThat(resp.unitPublicUuid()).isEqualTo(unitUuid);
            assertThat(resp.learningSessionPublicUuid()).isEqualTo(sessionUuid);
        }

        @Test
        @DisplayName("assignment label is formatted from course · section · teacher · period")
        void assignmentLabel() {
            EvaluationResponse resp = mapper.toResponse(evaluation, 0L);
            assertThat(resp.assignment().publicUuid()).isEqualTo(assignment.getPublicUuid());
            assertThat(resp.assignment().label()).contains("MAT");
            assertThat(resp.assignment().label()).contains("5A");
            assertThat(resp.assignment().label()).contains("Garcia");
            assertThat(resp.assignment().label()).contains("B1");
        }

        @Test
        @DisplayName("assignment label uses ? for missing parts")
        void assignmentLabelMissing() throws Exception {
            TeacherAssignment partial = new TeacherAssignment();
            setField(partial, "publicUuid", UUID.randomUUID());
            Evaluation eval = new Evaluation();
            setField(eval, "publicUuid", UUID.randomUUID());
            eval.setTeacherAssignment(partial);

            EvaluationResponse resp = mapper.toResponse(eval, 0L);

            assertThat(resp.assignment().label())
                    .contains("?")
                    .contains(" · ");
        }

        @Test
        @DisplayName("null assignment — assignment ref is null")
        void nullAssignment() {
            evaluation.setTeacherAssignment(null);
            EvaluationResponse resp = mapper.toResponse(evaluation, 0L);
            assertThat(resp.assignment()).isNull();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps fields without parent reference")
        void mapsFields() {
            var item = mapper.toListItem(evaluation, 25L);

            assertThat(item.publicUuid()).isEqualTo(evaluation.getPublicUuid());
            assertThat(item.kind()).isEqualTo(EvaluationKind.EXAM);
            assertThat(item.name()).isEqualTo("Midterm");
            assertThat(item.weight()).isEqualByComparingTo("25.00");
            assertThat(item.gradeCount()).isEqualTo(25L);
            assertThat(item.status()).isEqualTo(EvaluationStatus.PUBLISHED);
            assertThat(item.scale()).isEqualTo(EvaluationScale.SCORE_0_20);
            assertThat(item.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("maps required fields and starts in DRAFT")
        void createsEntity() {
            var req = new CreateEvaluationRequest(
                    EvaluationKind.QUIZ, "Quiz 1", null,
                    new BigDecimal("10.00"),
                    LocalDate.of(2026, 4, 1), null,
                    EvaluationScale.SCORE_0_20, null, null, Boolean.FALSE);

            Evaluation eval = mapper.fromCreate(req, assignment);

            assertThat(eval.getTeacherAssignment()).isSameAs(assignment);
            assertThat(eval.getKind()).isEqualTo(EvaluationKind.QUIZ);
            assertThat(eval.getName()).isEqualTo("Quiz 1");
            assertThat(eval.getWeight()).isEqualByComparingTo("10.00");
            assertThat(eval.getScheduledDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(eval.getDueDate()).isNull();
            assertThat(eval.getScale()).isEqualTo(EvaluationScale.SCORE_0_20);
            assertThat(eval.getStatus()).isEqualTo(EvaluationStatus.DRAFT);
            assertThat(eval.getIsActive()).isFalse();
            assertThat(eval.getUnit()).isNull();
            assertThat(eval.getLearningSession()).isNull();
        }

        @Test
        @DisplayName("name is trimmed")
        void trimsName() {
            var req = new CreateEvaluationRequest(
                    EvaluationKind.QUIZ, "  Quiz 1  ", null,
                    new BigDecimal("10.00"),
                    LocalDate.of(2026, 4, 1), null,
                    EvaluationScale.SCORE_0_20, null, null, null);
            Evaluation eval = mapper.fromCreate(req, assignment);
            assertThat(eval.getName()).isEqualTo("Quiz 1");
        }

        @Test
        @DisplayName("blank description becomes null")
        void blankDescription() {
            var req = new CreateEvaluationRequest(
                    EvaluationKind.QUIZ, "Quiz 1", "   ",
                    new BigDecimal("10.00"),
                    LocalDate.of(2026, 4, 1), null,
                    EvaluationScale.SCORE_0_20, null, null, null);
            Evaluation eval = mapper.fromCreate(req, assignment);
            assertThat(eval.getDescription()).isNull();
        }

        @Test
        @DisplayName("null isActive defaults to TRUE")
        void nullIsActiveDefaultsTrue() {
            var req = new CreateEvaluationRequest(
                    EvaluationKind.QUIZ, "Quiz 1", null,
                    new BigDecimal("10.00"),
                    LocalDate.of(2026, 4, 1), null,
                    EvaluationScale.SCORE_0_20, null, null, null);
            Evaluation eval = mapper.fromCreate(req, assignment);
            assertThat(eval.getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("partial-merge — non-null fields replace")
        void partialMerge() {
            UpdateEvaluationRequest patch = new UpdateEvaluationRequest(
                    EvaluationKind.QUIZ, null, "Updated description",
                    null, LocalDate.of(2026, 5, 15), null,
                    null, null, null, Boolean.FALSE);

            mapper.applyUpdate(patch, evaluation);

            assertThat(evaluation.getKind()).isEqualTo(EvaluationKind.QUIZ);
            assertThat(evaluation.getDescription()).isEqualTo("Updated description");
            assertThat(evaluation.getScheduledDate()).isEqualTo(LocalDate.of(2026, 5, 15));
            assertThat(evaluation.getIsActive()).isFalse();
            assertThat(evaluation.getName()).isEqualTo("Midterm");
            assertThat(evaluation.getWeight()).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("blank description is normalized to null")
        void blankDescription() {
            UpdateEvaluationRequest patch = new UpdateEvaluationRequest(
                    null, null, "   ", null, null, null,
                    null, null, null, null);
            mapper.applyUpdate(patch, evaluation);
            assertThat(evaluation.getDescription()).isNull();
        }

        @Test
        @DisplayName("all-null patch is a no-op")
        void noop() {
            String original = evaluation.getName();
            UpdateEvaluationRequest patch = new UpdateEvaluationRequest(
                    null, null, null, null, null, null,
                    null, null, null, null);
            mapper.applyUpdate(patch, evaluation);
            assertThat(evaluation.getName()).isEqualTo(original);
        }

        @Test
        @DisplayName("name is trimmed")
        void trimsName() {
            UpdateEvaluationRequest patch = new UpdateEvaluationRequest(
                    null, "  Renamed  ", null, null, null, null,
                    null, null, null, null);
            mapper.applyUpdate(patch, evaluation);
            assertThat(evaluation.getName()).isEqualTo("Renamed");
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private static Course newCourse(String code, String name) throws Exception {
        Course c = new Course();
        setField(c, "publicUuid", UUID.randomUUID());
        setField(c, "id", UUID.randomUUID());
        c.setCode(code);
        c.setName(name);
        return c;
    }

    private static Section newSection(String name) throws Exception {
        Section s = new Section();
        setField(s, "publicUuid", UUID.randomUUID());
        setField(s, "id", UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private static Teacher newTeacher(String last, String first) throws Exception {
        Teacher t = new Teacher();
        setField(t, "publicUuid", UUID.randomUUID());
        setField(t, "id", UUID.randomUUID());
        t.setLastName(last);
        t.setFirstName(first);
        return t;
    }

    private static AcademicPeriod newPeriod(PeriodType type, int ordinal, String name) throws Exception {
        AcademicPeriod p = new AcademicPeriod();
        setField(p, "publicUuid", UUID.randomUUID());
        setField(p, "id", UUID.randomUUID());
        p.setPeriodType(type);
        p.setOrdinal(ordinal);
        p.setName(name);
        return p;
    }

    private static TeacherAssignment newAssignment(Course course, Section section,
            Teacher teacher, AcademicPeriod period) throws Exception {
        TeacherAssignment a = new TeacherAssignment();
        setField(a, "publicUuid", UUID.randomUUID());
        setField(a, "id", UUID.randomUUID());
        a.setCourse(course);
        a.setSection(section);
        a.setTeacher(teacher);
        a.setAcademicPeriod(period);
        return a;
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