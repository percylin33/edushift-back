package com.edushift.modules.evaluations.graderecord.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.CreateGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordFilters;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.UpdateGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.error.GradeRecordErrorCodes;
import com.edushift.modules.evaluations.graderecord.mapper.GradeRecordMapper;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GradeRecordServiceImpl}. Covers the validation
 * matrix per scale, the lifecycle gate (CLOSED), the enrollment gate,
 * the upsert semantics and the atomic bulk endpoint.
 *
 * <p>Multi-tenant cross-isolation is covered by
 * {@code GradeRecordTenantIsolationIT} (Testcontainers Postgres).
 */
@ExtendWith(MockitoExtension.class)
class GradeRecordServiceImplTest {

    @Mock private GradeRecordRepository gradeRecordRepository;
    @Mock private EvaluationRepository evaluationRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentEnrollmentRepository enrollmentRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Spy private GradeRecordMapper mapper = new GradeRecordMapper();

    @InjectMocks private GradeRecordServiceImpl service;

    private UUID tenantId;
    private UUID currentUserId;
    private TeacherAssignment assignment;
    private Section section;
    private Evaluation scoreEvaluation;
    private Student student;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();

        section = new Section();
        setField(section, "publicUuid", UUID.randomUUID());

        assignment = new TeacherAssignment();
        setField(assignment, "publicUuid", UUID.randomUUID());
        assignment.setSection(section);

        scoreEvaluation = newEvaluation(
                EvaluationScale.SCORE_0_20, EvaluationStatus.PUBLISHED);

        student = new Student();
        setField(student, "publicUuid", UUID.randomUUID());
        student.setFirstName("Ana");
        student.setLastName("García");
    }

    // -------------------------------------------------------------------
    // Upsert (single)
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("upsertGrade")
    class UpsertGrade {

        @Test
        @DisplayName("creates a new grade when none exists for (eval, student)")
        void createsNew() {
            CreateGradeRecordRequest request = new CreateGradeRecordRequest(
                    student.getPublicUuid().toString(),
                    new BigDecimal("17.50"), null, "buen examen");

            given(evaluationRepository.findByPublicUuid(scoreEvaluation.getPublicUuid()))
                    .willReturn(Optional.of(scoreEvaluation));
            given(studentRepository.findByPublicUuid(student.getPublicUuid()))
                    .willReturn(Optional.of(student));
            given(enrollmentRepository.existsActiveAt(any(), any(), any()))
                    .willReturn(true);
            given(gradeRecordRepository.findByEvaluationAndStudent(scoreEvaluation, student))
                    .willReturn(Optional.empty());
            given(currentUserProvider.currentUserId()).willReturn(Optional.of(currentUserId));
            given(gradeRecordRepository.saveAndFlush(any(GradeRecord.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            GradeRecordResponse response = service.upsertGrade(
                    scoreEvaluation.getPublicUuid(), request);

            assertThat(response.score()).isEqualByComparingTo("17.50");
            assertThat(response.literal()).isNull();
            assertThat(response.recordedByUserId()).isEqualTo(currentUserId);
            verify(gradeRecordRepository, times(1)).saveAndFlush(any(GradeRecord.class));
        }

        @Test
        @DisplayName("updates the existing row when (eval, student) already exists")
        void updatesExisting() {
            GradeRecord existing = new GradeRecord();
            existing.setEvaluation(scoreEvaluation);
            existing.setStudent(student);
            existing.setScore(new BigDecimal("12.00"));
            existing.setRecordedAt(Instant.now().minusSeconds(3600));
            existing.setRecordedByUserId(UUID.randomUUID());

            CreateGradeRecordRequest request = new CreateGradeRecordRequest(
                    student.getPublicUuid().toString(),
                    new BigDecimal("18.00"), null, null);

            given(evaluationRepository.findByPublicUuid(scoreEvaluation.getPublicUuid()))
                    .willReturn(Optional.of(scoreEvaluation));
            given(studentRepository.findByPublicUuid(student.getPublicUuid()))
                    .willReturn(Optional.of(student));
            given(enrollmentRepository.existsActiveAt(any(), any(), any()))
                    .willReturn(true);
            given(gradeRecordRepository.findByEvaluationAndStudent(scoreEvaluation, student))
                    .willReturn(Optional.of(existing));
            given(currentUserProvider.currentUserId()).willReturn(Optional.of(currentUserId));
            given(gradeRecordRepository.saveAndFlush(any(GradeRecord.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            GradeRecordResponse response = service.upsertGrade(
                    scoreEvaluation.getPublicUuid(), request);

            assertThat(response.score()).isEqualByComparingTo("18.00");
            assertThat(existing.getRecordedByUserId()).isEqualTo(currentUserId);
        }

        @Test
        @DisplayName("rejects when the evaluation does not exist (404)")
        void evaluationNotFound() {
            UUID missing = UUID.randomUUID();
            given(evaluationRepository.findByPublicUuid(missing))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.upsertGrade(missing,
                    new CreateGradeRecordRequest(
                            UUID.randomUUID().toString(),
                            BigDecimal.TEN, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rejects when the parent evaluation is CLOSED")
        void closedEvaluation() {
            Evaluation closed = newEvaluation(
                    EvaluationScale.SCORE_0_20, EvaluationStatus.CLOSED);
            given(evaluationRepository.findByPublicUuid(closed.getPublicUuid()))
                    .willReturn(Optional.of(closed));

            assertThatThrownBy(() -> service.upsertGrade(closed.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            BigDecimal.TEN, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_EVAL_CLOSED);
        }

        @Test
        @DisplayName("rejects when the student is not enrolled in the section at the date")
        void studentNotEnrolled() {
            given(evaluationRepository.findByPublicUuid(scoreEvaluation.getPublicUuid()))
                    .willReturn(Optional.of(scoreEvaluation));
            given(studentRepository.findByPublicUuid(student.getPublicUuid()))
                    .willReturn(Optional.of(student));
            given(enrollmentRepository.existsActiveAt(any(), any(), any()))
                    .willReturn(false);

            assertThatThrownBy(() -> service.upsertGrade(scoreEvaluation.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            BigDecimal.TEN, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_STUDENT_NOT_ENROLLED);
        }

        @Test
        @DisplayName("rejects when no authenticated user is available")
        void noCurrentUser() {
            given(evaluationRepository.findByPublicUuid(scoreEvaluation.getPublicUuid()))
                    .willReturn(Optional.of(scoreEvaluation));
            given(studentRepository.findByPublicUuid(student.getPublicUuid()))
                    .willReturn(Optional.of(student));
            given(enrollmentRepository.existsActiveAt(any(), any(), any()))
                    .willReturn(true);
            given(gradeRecordRepository.findByEvaluationAndStudent(scoreEvaluation, student))
                    .willReturn(Optional.empty());
            given(currentUserProvider.currentUserId()).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.upsertGrade(scoreEvaluation.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            BigDecimal.TEN, null, null)))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // -------------------------------------------------------------------
    // Scale validation matrix
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("Scale validation")
    class ScaleValidation {

        @Test
        @DisplayName("SCORE_0_20: rejects literal — GRADE_VALUE_SHAPE_MISMATCH")
        void score020WithLiteralFails() {
            arrangeFoundEvaluationAndEnrolledStudent(scoreEvaluation);

            assertThatThrownBy(() -> service.upsertGrade(scoreEvaluation.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            null, "AD", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_VALUE_SHAPE_MISMATCH);
        }

        @Test
        @DisplayName("SCORE_0_20: rejects score = -1 — GRADE_SCORE_OUT_OF_RANGE")
        void score020NegativeFails() {
            arrangeFoundEvaluationAndEnrolledStudent(scoreEvaluation);

            assertThatThrownBy(() -> service.upsertGrade(scoreEvaluation.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            new BigDecimal("-1.00"), null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_SCORE_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("SCORE_0_20: rejects score = 21 — GRADE_SCORE_OUT_OF_RANGE")
        void score020AboveTwentyFails() {
            arrangeFoundEvaluationAndEnrolledStudent(scoreEvaluation);

            assertThatThrownBy(() -> service.upsertGrade(scoreEvaluation.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            new BigDecimal("21.00"), null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_SCORE_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("SCORE_0_20: rejects null score — GRADE_VALUE_REQUIRED")
        void score020WithoutScoreFails() {
            arrangeFoundEvaluationAndEnrolledStudent(scoreEvaluation);

            assertThatThrownBy(() -> service.upsertGrade(scoreEvaluation.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_VALUE_REQUIRED);
        }

        @Test
        @DisplayName("LITERAL_AD: accepts 'AD' / 'A' and rejects 'B'")
        void literalAdMatrix() {
            Evaluation eval = newEvaluation(
                    EvaluationScale.LITERAL_AD, EvaluationStatus.PUBLISHED);
            arrangeFoundEvaluationAndEnrolledStudent(eval);
            given(currentUserProvider.currentUserId())
                    .willReturn(Optional.of(currentUserId));
            given(gradeRecordRepository.findByEvaluationAndStudent(eval, student))
                    .willReturn(Optional.empty());
            given(gradeRecordRepository.saveAndFlush(any(GradeRecord.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            GradeRecordResponse okAD = service.upsertGrade(eval.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            null, "AD", null));
            assertThat(okAD.literal()).isEqualTo("AD");
            assertThat(okAD.score()).isNull();

            assertThatThrownBy(() -> service.upsertGrade(eval.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            null, "B", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_LITERAL_INVALID);
        }

        @Test
        @DisplayName("LITERAL_NA: rejects 'C' — GRADE_LITERAL_INVALID")
        void literalNaRejectsC() {
            Evaluation eval = newEvaluation(
                    EvaluationScale.LITERAL_NA, EvaluationStatus.PUBLISHED);
            arrangeFoundEvaluationAndEnrolledStudent(eval);

            assertThatThrownBy(() -> service.upsertGrade(eval.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            null, "C", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_LITERAL_INVALID);
        }

        @Test
        @DisplayName("LITERAL_A_B_C_D: accepts 'C' and rejects 'AD'")
        void literalAbcdMatrix() {
            Evaluation eval = newEvaluation(
                    EvaluationScale.LITERAL_A_B_C_D, EvaluationStatus.PUBLISHED);
            arrangeFoundEvaluationAndEnrolledStudent(eval);
            given(currentUserProvider.currentUserId())
                    .willReturn(Optional.of(currentUserId));
            given(gradeRecordRepository.findByEvaluationAndStudent(eval, student))
                    .willReturn(Optional.empty());
            given(gradeRecordRepository.saveAndFlush(any(GradeRecord.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            GradeRecordResponse ok = service.upsertGrade(eval.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            null, "C", null));
            assertThat(ok.literal()).isEqualTo("C");

            assertThatThrownBy(() -> service.upsertGrade(eval.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            null, "AD", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_LITERAL_INVALID);
        }

        @Test
        @DisplayName("LITERAL_*: rejects score sent alongside literal — shape mismatch")
        void literalWithScoreFails() {
            Evaluation eval = newEvaluation(
                    EvaluationScale.LITERAL_AD, EvaluationStatus.PUBLISHED);
            arrangeFoundEvaluationAndEnrolledStudent(eval);

            assertThatThrownBy(() -> service.upsertGrade(eval.getPublicUuid(),
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            new BigDecimal("18.00"), "AD", null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_VALUE_SHAPE_MISMATCH);
        }
    }

    // -------------------------------------------------------------------
    // Bulk
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("bulkUpsert")
    class BulkUpsert {

        @Test
        @DisplayName("happy path: persists every row in a single transaction")
        void happyPath() {
            Student studentB = new Student();
            setField(studentB, "publicUuid", UUID.randomUUID());
            studentB.setFirstName("Bruno");
            studentB.setLastName("Pérez");

            given(evaluationRepository.findByPublicUuid(scoreEvaluation.getPublicUuid()))
                    .willReturn(Optional.of(scoreEvaluation));
            given(studentRepository.findByPublicUuid(student.getPublicUuid()))
                    .willReturn(Optional.of(student));
            given(studentRepository.findByPublicUuid(studentB.getPublicUuid()))
                    .willReturn(Optional.of(studentB));
            given(enrollmentRepository.existsActiveAt(any(), any(), any()))
                    .willReturn(true);
            given(gradeRecordRepository.findByEvaluationAndStudent(any(), any()))
                    .willReturn(Optional.empty());
            given(currentUserProvider.currentUserId())
                    .willReturn(Optional.of(currentUserId));
            given(gradeRecordRepository.saveAndFlush(any(GradeRecord.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            BulkGradeRecordRequest payload = new BulkGradeRecordRequest(List.of(
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            new BigDecimal("18.00"), null, null),
                    new CreateGradeRecordRequest(
                            studentB.getPublicUuid().toString(),
                            new BigDecimal("14.50"), null, null)
            ));

            BulkGradeRecordResponse response = service.bulkUpsert(
                    scoreEvaluation.getPublicUuid(), payload);

            assertThat(response.requested()).isEqualTo(2);
            assertThat(response.created()).isEqualTo(2);
            assertThat(response.updated()).isZero();
            assertThat(response.records()).hasSize(2);
            verify(gradeRecordRepository, times(2)).saveAndFlush(any(GradeRecord.class));
        }

        @Test
        @DisplayName("one invalid row aborts the whole batch — GRADE_BULK_INVALID_ROW")
        void invalidRowAborts() {
            Student studentB = new Student();
            setField(studentB, "publicUuid", UUID.randomUUID());

            given(evaluationRepository.findByPublicUuid(scoreEvaluation.getPublicUuid()))
                    .willReturn(Optional.of(scoreEvaluation));
            given(studentRepository.findByPublicUuid(student.getPublicUuid()))
                    .willReturn(Optional.of(student));
            given(studentRepository.findByPublicUuid(studentB.getPublicUuid()))
                    .willReturn(Optional.of(studentB));
            given(enrollmentRepository.existsActiveAt(any(), any(), any()))
                    .willReturn(true);

            BulkGradeRecordRequest payload = new BulkGradeRecordRequest(List.of(
                    new CreateGradeRecordRequest(
                            student.getPublicUuid().toString(),
                            new BigDecimal("18.00"), null, null),
                    // Row 1 is bad: SCORE_0_20 evaluation, sending literal.
                    new CreateGradeRecordRequest(
                            studentB.getPublicUuid().toString(),
                            null, "AD", null)
            ));

            assertThatThrownBy(() -> service.bulkUpsert(
                    scoreEvaluation.getPublicUuid(), payload))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_BULK_INVALID_ROW);

            verify(gradeRecordRepository, never()).saveAndFlush(any(GradeRecord.class));
        }
    }

    // -------------------------------------------------------------------
    // Update / Delete
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("updateGrade")
    class UpdateGrade {

        @Test
        @DisplayName("rejects empty patches with EMPTY_PATCH")
        void rejectsEmptyPatch() {
            assertThatThrownBy(() -> service.updateGrade(UUID.randomUUID(),
                    new UpdateGradeRecordRequest(null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code", "EMPTY_PATCH");
        }

        @Test
        @DisplayName("rejects patches when parent evaluation is CLOSED")
        void rejectsClosed() {
            Evaluation closed = newEvaluation(
                    EvaluationScale.SCORE_0_20, EvaluationStatus.CLOSED);
            GradeRecord grade = new GradeRecord();
            grade.setEvaluation(closed);
            grade.setStudent(student);
            grade.setScore(new BigDecimal("12.00"));
            UUID gradeUuid = UUID.randomUUID();
            setField(grade, "publicUuid", gradeUuid);

            given(gradeRecordRepository.findByPublicUuid(gradeUuid))
                    .willReturn(Optional.of(grade));

            assertThatThrownBy(() -> service.updateGrade(gradeUuid,
                    new UpdateGradeRecordRequest(new BigDecimal("18.00"), null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_EVAL_CLOSED);
        }
    }

    @Nested
    @DisplayName("deleteGrade")
    class DeleteGrade {

        @Test
        @DisplayName("soft-deletes a grade attached to a DRAFT evaluation")
        void deletesOk() {
            Evaluation draft = newEvaluation(
                    EvaluationScale.SCORE_0_20, EvaluationStatus.DRAFT);
            GradeRecord grade = new GradeRecord();
            grade.setEvaluation(draft);
            grade.setStudent(student);
            UUID uuid = UUID.randomUUID();
            setField(grade, "publicUuid", uuid);

            given(gradeRecordRepository.findByPublicUuid(uuid))
                    .willReturn(Optional.of(grade));

            service.deleteGrade(uuid);

            verify(gradeRecordRepository, times(1)).delete(grade);
        }

        @Test
        @DisplayName("rejects delete when parent evaluation is CLOSED")
        void rejectsClosed() {
            Evaluation closed = newEvaluation(
                    EvaluationScale.SCORE_0_20, EvaluationStatus.CLOSED);
            GradeRecord grade = new GradeRecord();
            grade.setEvaluation(closed);
            UUID uuid = UUID.randomUUID();
            setField(grade, "publicUuid", uuid);

            given(gradeRecordRepository.findByPublicUuid(uuid))
                    .willReturn(Optional.of(grade));

            assertThatThrownBy(() -> service.deleteGrade(uuid))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code",
                            GradeRecordErrorCodes.GRADE_EVAL_CLOSED);
            verify(gradeRecordRepository, never()).delete(any(GradeRecord.class));
        }
    }

    // -------------------------------------------------------------------
    // Read endpoints
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("listGrades")
    class ListGrades {

        @Test
        @DisplayName("returns empty list when no rows match")
        void emptyMatches() {
            given(evaluationRepository.findByPublicUuid(scoreEvaluation.getPublicUuid()))
                    .willReturn(Optional.of(scoreEvaluation));
            given(gradeRecordRepository.findFilteredByEvaluation(
                    scoreEvaluation.getPublicUuid(), null, null, null))
                    .willReturn(List.of());

            assertThat(service.listGrades(
                    scoreEvaluation.getPublicUuid(),
                    new GradeRecordFilters(null, null, null)))
                    .isEmpty();
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private void arrangeFoundEvaluationAndEnrolledStudent(Evaluation evaluation) {
        given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                .willReturn(Optional.of(evaluation));
        given(studentRepository.findByPublicUuid(student.getPublicUuid()))
                .willReturn(Optional.of(student));
        given(enrollmentRepository.existsActiveAt(any(), any(), any()))
                .willReturn(true);
    }

    private Evaluation newEvaluation(EvaluationScale scale, EvaluationStatus status) {
        Evaluation evaluation = new Evaluation();
        setField(evaluation, "publicUuid", UUID.randomUUID());
        evaluation.setTeacherAssignment(assignment);
        evaluation.setScale(scale);
        evaluation.setStatus(status);
        evaluation.setName("Examen Bimestre I");
        evaluation.setScheduledDate(LocalDate.now());
        return evaluation;
    }

    // -- reflection helpers (publicUuid, id are typically @Generated) --

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

    private static Field findField(Class<?> type, String name)
            throws NoSuchFieldException {
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
