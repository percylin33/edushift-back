package com.edushift.modules.me.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.period.support.AcademicPeriodResolver;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.graderegister.dto.GradeCategoryResponse;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateResponse;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterTemplateService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.materials.repository.MaterialRepository;
import com.edushift.modules.me.dto.MeGradesSummaryResponse;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.repository.TaskRepository;
import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import com.edushift.modules.tasks.submission.repository.SubmissionRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeAcademicServiceImplGradesSummaryTest {

    @Mock StudentRepository studentRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock SectionRepository sectionRepository;
    @Mock MaterialRepository materialRepository;
    @Mock TaskRepository taskRepository;
    @Mock QuizRepository quizRepository;
    @Mock QuizQuestionRepository quizQuestionRepository;
    @Mock TeacherAssignmentRepository teacherAssignmentRepository;
    @Mock GradeRecordRepository gradeRecordRepository;
    @Mock EvaluationRepository evaluationRepository;
    @Mock GradeRegisterTemplateService gradeRegisterTemplateService;
    @Mock SubmissionRepository submissionRepository;
    @Mock AcademicPeriodResolver academicPeriodResolver;
    @Mock AcademicPeriodRepository academicPeriodRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock FileObjectService fileObjectService;

    @InjectMocks MeAcademicServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private Student student;
    private Section section;
    private AcademicPeriod currentPeriod;
    private TeacherAssignment math;

    @BeforeEach
    void setUp() {
        student = new Student();
        student.setId(UUID.randomUUID());
        student.setPublicUuid(UUID.randomUUID());
        student.setUserId(userId);

        section = new Section();
        section.setId(UUID.randomUUID());
        section.setPublicUuid(UUID.randomUUID());
        section.setName("3° A");

        currentPeriod = period("Bimestre I");
        math = assignment("Matemática", "Carlos Paredes", currentPeriod);

        given(currentUserProvider.currentUserId()).willReturn(Optional.of(userId));
        given(studentRepository.findByUserId(userId)).willReturn(Optional.of(student));
        given(academicPeriodResolver.resolveCurrentPeriod()).willReturn(Optional.of(currentPeriod));
    }

    @Test
    void numericAverageMatchesTheGradeBookAndIgnoresOtherStudents() {
        Evaluation tarea = evaluation(math, "Tarea", EvaluationKind.TASK, new BigDecimal("1"), null);
        Evaluation examen = evaluation(math, "Examen", EvaluationKind.EXAM, new BigDecimal("3"), null);
        GradeRecord own = grade(tarea, student, new BigDecimal("20"), null);
        GradeRecord foreign = grade(examen, otherStudent(), new BigDecimal("0"), null);
        wire(List.of(math), List.of(own, foreign), List.of());
        given(evaluationRepository.findAllByAssignment(math)).willReturn(List.of(tarea, examen));
        given(gradeRegisterTemplateService.resolveForAssignment(math)).willReturn(Optional.empty());

        MeGradesSummaryResponse summary = service.summarizeMyGrades(null);

        assertThat(summary.overallAverage()).isEqualByComparingTo("20.00");
        assertThat(summary.courses()).hasSize(1);
        assertThat(summary.courses().getFirst().average()).isEqualByComparingTo("20.00");
        assertThat(summary.items()).extracting(MeGradesSummaryResponse.Item::evaluationName)
                .containsExactly("Tarea");
        assertThat(summary.items().getFirst().countsTowardAverage()).isTrue();
        verify(gradeRecordRepository, never()).findAllByAssignment(any());
    }

    @Test
    void categoryTemplateUsesCategoryWeights() {
        GradeCategory practicas = new GradeCategory();
        practicas.setPublicUuid(UUID.randomUUID());
        GradeCategory examenCat = new GradeCategory();
        examenCat.setPublicUuid(UUID.randomUUID());
        Evaluation p = evaluation(math, "Práctica", EvaluationKind.TASK, BigDecimal.ONE, practicas);
        Evaluation e = evaluation(math, "Examen", EvaluationKind.EXAM, BigDecimal.ONE, examenCat);
        wire(List.of(math), List.of(
                grade(p, student, new BigDecimal("10"), null),
                grade(e, student, new BigDecimal("20"), null)), List.of());
        given(evaluationRepository.findAllByAssignment(math)).willReturn(List.of(p, e));
        given(gradeRegisterTemplateService.resolveForAssignment(math)).willReturn(Optional.of(
                template(EvaluationScale.SCORE_0_20, List.of(
                        category(practicas.getPublicUuid(), "Prácticas", "40"),
                        category(examenCat.getPublicUuid(), "Examen", "60")))));

        MeGradesSummaryResponse summary = service.summarizeMyGrades(null);

        assertThat(summary.courses().getFirst().average()).isEqualByComparingTo("16.00");
        assertThat(summary.overallAverage()).isEqualByComparingTo("16.00");
        assertThat(summary.courses().getFirst().categories())
                .extracting(MeGradesSummaryResponse.Category::name)
                .containsExactly("Prácticas", "Examen");
    }

    @Test
    void gradesOutsideTheTemplateStayEvidenceAndDoNotCreateAnAverage() {
        GradeCategory emptySlot = new GradeCategory();
        emptySlot.setPublicUuid(UUID.randomUUID());
        Evaluation practica = evaluation(math, "PRACTICA 1", EvaluationKind.QUIZ, new BigDecimal("25"), null);
        Evaluation tarea = evaluation(math, "tarea", EvaluationKind.TASK, new BigDecimal("25"), null);
        Submission task = gradedTask(math, "FIREBASE TAREA 2", (short) 15, (short) 20);
        wire(List.of(math), List.of(
                grade(practica, student, new BigDecimal("17"), null),
                grade(tarea, student, new BigDecimal("20"), null)), List.of(task));
        given(evaluationRepository.findAllByAssignment(math)).willReturn(List.of(practica, tarea));
        given(gradeRegisterTemplateService.resolveForAssignment(math)).willReturn(Optional.of(
                template(EvaluationScale.SCORE_0_20, List.of(
                        category(emptySlot.getPublicUuid(), "Participación oral", "14.28")))));

        MeGradesSummaryResponse summary = service.summarizeMyGrades(null);

        assertThat(summary.courses().getFirst().average()).isNull();
        assertThat(summary.overallAverage()).isNull();
        assertThat(summary.gradedCourseCount()).isZero();
        assertThat(summary.courses().getFirst().categories())
                .extracting(MeGradesSummaryResponse.Category::name)
                .containsExactly("Participación oral");
        assertThat(summary.items())
                .filteredOn(item -> "PRACTICA 1".equals(item.evaluationName()))
                .allMatch(item -> "EVIDENCE".equals(item.role()) && !item.countsTowardAverage());
        assertThat(summary.items())
                .filteredOn(item -> "FIREBASE TAREA 2".equals(item.evaluationName()))
                .allMatch(item -> "CLASSWORK".equals(item.role()) && !item.countsTowardAverage());
    }

    @Test
    void anotherPeriodDoesNotMixGrades() {
        AcademicPeriod other = period("Bimestre II");
        TeacherAssignment history = assignment("Historia", "Fernando Ruiz", other);
        Evaluation currentEval = evaluation(math, "Actual", EvaluationKind.QUIZ, BigDecimal.ONE, null);
        Evaluation otherEval = evaluation(history, "Ajena", EvaluationKind.QUIZ, BigDecimal.ONE, null);
        wire(List.of(math, history), List.of(
                grade(currentEval, student, new BigDecimal("15"), null),
                grade(otherEval, student, new BigDecimal("10"), null)), List.of());
        given(evaluationRepository.findAllByAssignment(math)).willReturn(List.of(currentEval));
        given(gradeRegisterTemplateService.resolveForAssignment(math)).willReturn(Optional.empty());

        MeGradesSummaryResponse summary = service.summarizeMyGrades(null);

        assertThat(summary.period().name()).isEqualTo("Bimestre I");
        assertThat(summary.period().current()).isTrue();
        assertThat(summary.courses()).extracting(MeGradesSummaryResponse.Course::courseName)
                .containsExactly("Matemática");
        assertThat(summary.items()).extracting(MeGradesSummaryResponse.Item::evaluationName)
                .containsExactly("Actual");
    }

    @Test
    void lmsTaskIsListedAndDoesNotMoveTheAverage() {
        Evaluation examen = evaluation(math, "Examen", EvaluationKind.EXAM, BigDecimal.ONE, null);
        Submission submission = gradedTask(math, "FIREBASE TAREA", (short) 8, (short) 20);
        wire(List.of(math), List.of(grade(examen, student, new BigDecimal("16"), null)), List.of(submission));
        given(evaluationRepository.findAllByAssignment(math)).willReturn(List.of(examen));
        given(gradeRegisterTemplateService.resolveForAssignment(math)).willReturn(Optional.empty());

        MeGradesSummaryResponse summary = service.summarizeMyGrades(null);

        assertThat(summary.courses().getFirst().average()).isEqualByComparingTo("16.00");
        assertThat(summary.overallAverage()).isEqualByComparingTo("16.00");
        MeGradesSummaryResponse.Item task = summary.items().stream()
                .filter(item -> "TASK".equals(item.source()))
                .findFirst()
                .orElseThrow();
        assertThat(task.countsTowardAverage()).isFalse();
        assertThat(task.evaluationName()).isEqualTo("FIREBASE TAREA");
        assertThat(task.score()).isEqualByComparingTo("8");
    }

    @Test
    void cnebCourseShowsALetterAndStaysOutOfTheNumericMean() {
        TeacherAssignment comunicacion = assignment("Comunicación", "Mariana Vega", currentPeriod);
        Evaluation competencia = evaluation(
                comunicacion, "Competencia 1", EvaluationKind.COMPETENCY, BigDecimal.ONE, null);
        competencia.setScale(EvaluationScale.LITERAL_CNEB);
        Evaluation mathEval = evaluation(math, "Examen", EvaluationKind.EXAM, BigDecimal.ONE, null);
        wire(List.of(comunicacion, math), List.of(
                grade(competencia, student, null, "AD"),
                grade(mathEval, student, new BigDecimal("18"), null)), List.of());
        given(evaluationRepository.findAllByAssignment(comunicacion)).willReturn(List.of(competencia));
        given(evaluationRepository.findAllByAssignment(math)).willReturn(List.of(mathEval));
        given(gradeRegisterTemplateService.resolveForAssignment(comunicacion)).willReturn(Optional.of(
                template(EvaluationScale.LITERAL_CNEB, List.of())));
        given(gradeRegisterTemplateService.resolveForAssignment(math)).willReturn(Optional.empty());

        MeGradesSummaryResponse summary = service.summarizeMyGrades(null);

        MeGradesSummaryResponse.Course letters = summary.courses().stream()
                .filter(course -> "Comunicación".equals(course.courseName()))
                .findFirst()
                .orElseThrow();
        assertThat(letters.literalAverage()).isEqualTo("AD");
        assertThat(letters.average()).isNull();
        assertThat(letters.scale()).isEqualTo("LITERAL_CNEB");
        assertThat(summary.overallAverage()).isEqualByComparingTo("18.00");
    }

    @Test
    void unknownPeriodIsNotFound() {
        given(academicPeriodRepository.findByPublicUuid(any())).willReturn(Optional.empty());
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> service.summarizeMyGrades(missing))
                .isInstanceOf(NotFoundException.class);
    }

    private void wire(List<TeacherAssignment> assignments, List<GradeRecord> grades, List<Submission> tasks) {
        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
        enrollment.setSection(section);
        enrollment.setStudent(student);
        enrollment.setEnrolledAt(LocalDate.now());
        given(enrollmentRepository.findActiveByStudentFetchSection(student)).willReturn(List.of(enrollment));
        given(sectionRepository.findById(section.getId())).willReturn(Optional.of(section));
        given(teacherAssignmentRepository.findAllBySectionActive(section, null)).willReturn(assignments);
        given(gradeRecordRepository.findAllByStudentPublicUuid(student.getPublicUuid())).willReturn(grades);
        given(submissionRepository.findAllByStudentUserIdAndStatus(userId, SubmissionStatus.GRADED))
                .willReturn(tasks);
    }

    private TeacherAssignment assignment(String courseName, String teacherName, AcademicPeriod period) {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setPublicUuid(UUID.randomUUID());
        course.setName(courseName);
        Teacher teacher = new Teacher();
        teacher.setFirstName(teacherName);
        teacher.setLastName("");
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setPublicUuid(UUID.randomUUID());
        assignment.setCourse(course);
        assignment.setSection(section);
        assignment.setTeacher(teacher);
        assignment.setAcademicPeriod(period);
        return assignment;
    }

    private static AcademicPeriod period(String name) {
        AcademicPeriod period = new AcademicPeriod();
        period.setId(UUID.randomUUID());
        period.setPublicUuid(UUID.randomUUID());
        period.setName(name);
        return period;
    }

    private static Evaluation evaluation(
            TeacherAssignment assignment,
            String name,
            EvaluationKind kind,
            BigDecimal weight,
            GradeCategory category) {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(UUID.randomUUID());
        evaluation.setPublicUuid(UUID.randomUUID());
        evaluation.setTeacherAssignment(assignment);
        evaluation.setName(name);
        evaluation.setKind(kind);
        evaluation.setScale(EvaluationScale.SCORE_0_20);
        evaluation.setStatus(EvaluationStatus.PUBLISHED);
        evaluation.setWeight(weight);
        evaluation.setScheduledDate(LocalDate.of(2026, 9, 3));
        evaluation.setCategory(category);
        return evaluation;
    }

    private static GradeRecord grade(Evaluation evaluation, Student owner, BigDecimal score, String literal) {
        GradeRecord record = new GradeRecord();
        record.setId(UUID.randomUUID());
        record.setPublicUuid(UUID.randomUUID());
        record.setEvaluation(evaluation);
        record.setStudent(owner);
        record.setScore(score);
        record.setLiteral(literal);
        record.setComments("Bien");
        record.setRecordedAt(Instant.parse("2026-09-03T15:00:00Z"));
        return record;
    }

    private static Student otherStudent() {
        Student other = new Student();
        other.setId(UUID.randomUUID());
        other.setPublicUuid(UUID.randomUUID());
        return other;
    }

    private static Submission gradedTask(
            TeacherAssignment assignment, String title, short grade, short maxScore) {
        Task task = new Task();
        task.setPublicUuid(UUID.randomUUID());
        task.setTitle(title);
        task.setTeacherAssignment(assignment);
        task.setSection(assignment.getSection());
        task.setMaxScore(maxScore);
        Submission submission = new Submission();
        submission.setPublicUuid(UUID.randomUUID());
        submission.setTask(task);
        submission.setGrade(grade);
        submission.setFeedback("Faltó seguridad");
        submission.setGradedAt(Instant.parse("2026-09-02T15:00:00Z"));
        submission.setStatus(SubmissionStatus.GRADED);
        return submission;
    }

    private static GradeCategoryResponse category(UUID id, String name, String weight) {
        return new GradeCategoryResponse(
                id, name, null, 1, new BigDecimal(weight), true, true, null, null, null, null);
    }

    private static GradeRegisterTemplateResponse template(
            EvaluationScale scale, List<GradeCategoryResponse> categories) {
        return new GradeRegisterTemplateResponse(
                UUID.randomUUID(), "Plantilla", null, null, false, false, null,
                null, null, null, null, null, null, null, null,
                categories, List.of(), null, null, scale);
    }
}
