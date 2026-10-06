package com.edushift.modules.evaluations.gradebook.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookStudentEntry;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterTemplateService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.tenants.branding.CurrentTenantLogoLoader;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GradeBookServiceImpl} (Sprint 5B / BE-5B.4).
 *
 * <p>The matrix shape, the inclusion rules ({@code PUBLISHED} or
 * {@code CLOSED} + numeric scale + recorded score) and the
 * weighted-average formula are all in scope here. The query side
 * (4 round-trips, single tenant filter) is covered by
 * {@code GradeBookControllerTest} (WebMvc) and the (compiles, pending
 * Docker) {@code EvaluationRubricTenantIsolationIT}.</p>
 */
@ExtendWith(MockitoExtension.class)
class GradeBookServiceImplTest {

    @Mock private TeacherAssignmentRepository assignmentRepository;
    @Mock private StudentEnrollmentRepository enrollmentRepository;
    @Mock private EvaluationRepository evaluationRepository;
    @Mock private GradeRecordRepository gradeRecordRepository;
    @Mock private GradeRegisterTemplateService gradeRegisterTemplateService;
    @Mock private com.edushift.modules.evaluations.graderegister.service.GradeRegisterColumnMaterializer columnMaterializer;
    @Mock private CurrentTenantLogoLoader tenantLogoLoader;

    @InjectMocks private GradeBookServiceImpl service;

    private TeacherAssignment assignment;
    private Section section;
    private Course course;
    private Teacher teacher;
    private AcademicPeriod period;
    private Grade grade;
    private AcademicLevel level;

    @BeforeEach
    void setUp() {
        level = new AcademicLevel();
        setField(level, "publicUuid", UUID.randomUUID());
        setField(level, "id", UUID.randomUUID());
        level.setCode("PRIMARIA");
        level.setName("Primaria");

        grade = new Grade();
        setField(grade, "publicUuid", UUID.randomUUID());
        setField(grade, "id", UUID.randomUUID());
        grade.setName("3° Primaria");
        grade.setOrdinal(3);
        grade.setLevel(level);

        section = new Section();
        setField(section, "publicUuid", UUID.randomUUID());
        setField(section, "id", UUID.randomUUID());
        section.setName("3°A");
        section.setGrade(grade);

        course = new Course();
        setField(course, "publicUuid", UUID.randomUUID());
        setField(course, "id", UUID.randomUUID());
        course.setName("Matemática");
        course.setCode("MAT");

        teacher = new Teacher();
        setField(teacher, "publicUuid", UUID.randomUUID());
        setField(teacher, "id", UUID.randomUUID());
        teacher.setFirstName("Ana");
        teacher.setLastName("García");
        teacher.setSecondLastName("Pérez");

        period = new AcademicPeriod();
        setField(period, "publicUuid", UUID.randomUUID());
        setField(period, "id", UUID.randomUUID());
        period.setPeriodType(PeriodType.BIMESTRE);
        period.setOrdinal(2);
        period.setName("Segundo bimestre");

        assignment = new TeacherAssignment();
        setField(assignment, "publicUuid", UUID.randomUUID());
        setField(assignment, "id", UUID.randomUUID());
        assignment.setSection(section);
        assignment.setCourse(course);
        assignment.setTeacher(teacher);
        assignment.setAcademicPeriod(period);
        assignment.setAssignedAt(Instant.now());
    }

    // =========================================================================
    // Header (assignment / section / course)
    // =========================================================================

    @Test
    @DisplayName("unknown assignment → 404 RESOURCE_NOT_FOUND")
    void unknownAssignment() {
        UUID anyUuid = UUID.randomUUID();
        given(assignmentRepository.findByPublicUuid(anyUuid))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildGradeBook(anyUuid))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("denormalises the assignment header (section, course, teacher, period)")
    void headerDenormalised() {
        givenAssignmentLoaded();
        givenEnrollments();
        givenEvaluations();
        givenGrades();

        GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

        assertThat(response.assignmentPublicUuid())
                .isEqualTo(assignment.getPublicUuid());
        assertThat(response.sectionPublicUuid())
                .isEqualTo(section.getPublicUuid());
        assertThat(response.sectionName()).isEqualTo("3°A");
        assertThat(response.gradePublicUuid()).isEqualTo(grade.getPublicUuid());
        assertThat(response.gradeName()).isEqualTo("3° Primaria");
        assertThat(response.gradeOrdinal()).isEqualTo(3);
        assertThat(response.levelCode()).isEqualTo("PRIMARIA");
        assertThat(response.coursePublicUuid()).isEqualTo(course.getPublicUuid());
        assertThat(response.courseName()).isEqualTo("Matemática");
        assertThat(response.teacherFullName()).isEqualTo("Ana García Pérez");
        assertThat(response.academicPeriodPublicUuid()).isEqualTo(period.getPublicUuid());
        assertThat(response.periodType()).isEqualTo(PeriodType.BIMESTRE);
        assertThat(response.periodOrdinal()).isEqualTo(2);
        assertThat(response.periodName()).isEqualTo("Segundo bimestre");
    }

    // =========================================================================
    // Empty-state behaviour
    // =========================================================================

    @Nested
    @DisplayName("empty state")
    class EmptyState {

        @Test
        @DisplayName("no enrollments + no evaluations → 200 with empty arrays")
        void allEmpty() {
            givenAssignmentLoaded();
            givenEnrollments();
            givenEvaluations();
            givenGrades();

            GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

            assertThat(response.students()).isEmpty();
            assertThat(response.evaluations()).isEmpty();
            assertThat(response.cells()).isEmpty();
        }

        @Test
        @DisplayName("students enrolled but no evaluations → average is null per student")
        void noEvaluations() {
            Student ana = newStudent("Ana", "García", null);
            givenAssignmentLoaded();
            givenEnrollments(newActiveEnrollment(ana));
            givenEvaluations();
            givenGrades();

            GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

            assertThat(response.students()).hasSize(1);
            assertThat(response.students().getFirst().weightedAverage()).isNull();
            assertThat(response.evaluations()).isEmpty();
            assertThat(response.cells()).isEmpty();
        }
    }

    // =========================================================================
    // Weighted average rules
    // =========================================================================

    @Nested
    @DisplayName("weighted average")
    class WeightedAverage {

        @Test
        @DisplayName("two PUBLISHED numeric evaluations weighted equally")
        void simpleAverage() {
            Student ana = newStudent("Ana", "García", null);
            Evaluation e1 = newEval("Tarea 1", EvaluationStatus.PUBLISHED,
                    EvaluationScale.SCORE_0_20, BigDecimal.ONE);
            Evaluation e2 = newEval("Examen", EvaluationStatus.PUBLISHED,
                    EvaluationScale.SCORE_0_20, BigDecimal.ONE);

            givenAssignmentLoaded();
            givenEnrollments(newActiveEnrollment(ana));
            givenEvaluations(e1, e2);
            givenGrades(
                    gradeFor(e1, ana, BigDecimal.valueOf(16), null),
                    gradeFor(e2, ana, BigDecimal.valueOf(12), null));

            GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

            // (16*1 + 12*1) / (1+1) = 14.00
            assertThat(response.students().getFirst().weightedAverage())
                    .isEqualByComparingTo(BigDecimal.valueOf(14.00));
        }

        @Test
        @DisplayName("evaluation with bigger weight pulls the average")
        void weightedFormula() {
            Student ana = newStudent("Ana", "García", null);
            Evaluation tarea = newEval("Tarea", EvaluationStatus.PUBLISHED,
                    EvaluationScale.SCORE_0_20, BigDecimal.valueOf(1));
            Evaluation examen = newEval("Examen", EvaluationStatus.CLOSED,
                    EvaluationScale.SCORE_0_20, BigDecimal.valueOf(3));

            givenAssignmentLoaded();
            givenEnrollments(newActiveEnrollment(ana));
            givenEvaluations(tarea, examen);
            givenGrades(
                    gradeFor(tarea, ana, BigDecimal.valueOf(20), null),
                    gradeFor(examen, ana, BigDecimal.valueOf(12), null));

            GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

            // (20*1 + 12*3) / (1+3) = 56/4 = 14.00
            assertThat(response.students().getFirst().weightedAverage())
                    .isEqualByComparingTo(BigDecimal.valueOf(14.00));
        }

        @Test
        @DisplayName("DRAFT evaluations are excluded from the average")
        void draftExcluded() {
            Student ana = newStudent("Ana", "García", null);
            Evaluation draft = newEval("Borrador", EvaluationStatus.DRAFT,
                    EvaluationScale.SCORE_0_20, BigDecimal.ONE);
            Evaluation published = newEval("Examen", EvaluationStatus.PUBLISHED,
                    EvaluationScale.SCORE_0_20, BigDecimal.ONE);

            givenAssignmentLoaded();
            givenEnrollments(newActiveEnrollment(ana));
            givenEvaluations(draft, published);
            givenGrades(
                    // The DRAFT row even has a "rehearsal" grade in the
                    // matrix — the FE should still see it as a cell, but
                    // it is ignored for the weighted average.
                    gradeFor(draft, ana, BigDecimal.valueOf(20), null),
                    gradeFor(published, ana, BigDecimal.valueOf(10), null));

            GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

            // Only the PUBLISHED row counts: 10/1 = 10.00
            GradeBookStudentEntry entry = response.students().getFirst();
            assertThat(entry.weightedAverage()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
            // ...but both cells are present in the matrix payload
            assertThat(response.cells()).hasSize(2);
        }

        @Test
        @DisplayName("literal-scale evaluations are excluded — all-RUBRIC → null")
        void allLiteralScalesYieldNull() {
            Student ana = newStudent("Ana", "García", null);
            Evaluation rubricEval = newEval("Rúbrica", EvaluationStatus.PUBLISHED,
                    EvaluationScale.LITERAL_AD, BigDecimal.ONE);
            rubricEval.setKind(EvaluationKind.RUBRIC);

            givenAssignmentLoaded();
            givenEnrollments(newActiveEnrollment(ana));
            givenEvaluations(rubricEval);
            givenGrades(gradeFor(rubricEval, ana, null, "AD"));

            GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

            assertThat(response.students().getFirst().weightedAverage()).isNull();
            assertThat(response.cells()).hasSize(1);
            assertThat(response.cells().getFirst().literal()).isEqualTo("AD");
        }

        @Test
        @DisplayName("missing cells (student without grade) skip but do not "
                + "penalise the rest of the average")
        void missingCellsSkipped() {
            Student ana = newStudent("Ana", "García", null);
            Evaluation e1 = newEval("E1", EvaluationStatus.PUBLISHED,
                    EvaluationScale.SCORE_0_20, BigDecimal.ONE);
            Evaluation e2 = newEval("E2", EvaluationStatus.PUBLISHED,
                    EvaluationScale.SCORE_0_20, BigDecimal.ONE);

            givenAssignmentLoaded();
            givenEnrollments(newActiveEnrollment(ana));
            givenEvaluations(e1, e2);
            // Only E1 has a grade for Ana — E2 cell is missing.
            givenGrades(gradeFor(e1, ana, BigDecimal.valueOf(15), null));

            GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

            // Average over the contributing eval only → 15.00
            assertThat(response.students().getFirst().weightedAverage())
                    .isEqualByComparingTo(BigDecimal.valueOf(15.00));
        }

        @Test
        @DisplayName("zero-sum weights → average is null (no division by zero)")
        void zeroWeightsYieldsNull() {
            Student ana = newStudent("Ana", "García", null);
            Evaluation zeroWeight = newEval("Optional", EvaluationStatus.PUBLISHED,
                    EvaluationScale.SCORE_0_20, BigDecimal.ZERO);

            givenAssignmentLoaded();
            givenEnrollments(newActiveEnrollment(ana));
            givenEvaluations(zeroWeight);
            givenGrades(gradeFor(zeroWeight, ana, BigDecimal.valueOf(20), null));

            GradeBookResponse response = service.buildGradeBook(assignment.getPublicUuid());

            assertThat(response.students().getFirst().weightedAverage()).isNull();
        }
    }

    // =========================================================================
    // Mock helpers (chainable per-test)
    // =========================================================================

    private void givenAssignmentLoaded() {
        given(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
                .willReturn(Optional.of(assignment));
        given(gradeRegisterTemplateService.resolveForAssignment(any()))
                .willReturn(Optional.empty());
    }

    private void givenEnrollments(StudentEnrollment... rows) {
        given(enrollmentRepository.findBySectionAndStatusIn(any(), any()))
                .willReturn(List.of(rows));
    }

    private void givenEvaluations(Evaluation... rows) {
        given(evaluationRepository.findAllByAssignment(assignment))
                .willReturn(List.of(rows));
    }

    private void givenGrades(GradeRecord... rows) {
        given(gradeRecordRepository.findAllByAssignment(assignment))
                .willReturn(List.of(rows));
    }

    // =========================================================================
    // Fixture builders
    // =========================================================================

    private static Student newStudent(String first, String last, String secondLast) {
        Student s = new Student();
        setField(s, "publicUuid", UUID.randomUUID());
        setField(s, "id", UUID.randomUUID());
        s.setFirstName(first);
        s.setLastName(last);
        s.setSecondLastName(secondLast);
        return s;
    }

    private StudentEnrollment newActiveEnrollment(Student student) {
        StudentEnrollment e = new StudentEnrollment();
        setField(e, "publicUuid", UUID.randomUUID());
        setField(e, "id", UUID.randomUUID());
        e.setStudent(student);
        e.setSection(section);
        e.setEnrolledAt(LocalDate.now().minusMonths(1));
        return e;
    }

    private Evaluation newEval(String name, EvaluationStatus status,
            EvaluationScale scale, BigDecimal weight) {
        Evaluation e = new Evaluation();
        e.setTeacherAssignment(assignment);
        e.setName(name);
        e.setKind(scale == EvaluationScale.SCORE_0_20
                ? EvaluationKind.TASK : EvaluationKind.RUBRIC);
        e.setScale(scale);
        e.setStatus(status);
        e.setWeight(weight);
        e.setScheduledDate(LocalDate.now());
        e.setIsActive(Boolean.TRUE);
        setField(e, "publicUuid", UUID.randomUUID());
        setField(e, "id", UUID.randomUUID());
        return e;
    }

    private static GradeRecord gradeFor(Evaluation eval, Student student,
            BigDecimal score, String literal) {
        GradeRecord g = new GradeRecord();
        setField(g, "publicUuid", UUID.randomUUID());
        setField(g, "id", UUID.randomUUID());
        g.setEvaluation(eval);
        g.setStudent(student);
        g.setScore(score);
        g.setLiteral(literal);
        g.setRecordedAt(Instant.now());
        return g;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
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
