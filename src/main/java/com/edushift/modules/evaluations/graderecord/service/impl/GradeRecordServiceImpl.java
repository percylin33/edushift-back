package com.edushift.modules.evaluations.graderecord.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.CreateGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordFilters;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordListItem;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.UpdateGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.error.GradeRecordErrorCodes;
import com.edushift.modules.evaluations.graderecord.mapper.GradeRecordMapper;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.graderecord.service.GradeRecordService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation for the {@link GradeRecordService} contract. Owns all
 * the business rules that the {@code grade_records} table enforces only
 * partially at the DB level (per-scale shape, lifecycle gate, enrollment
 * gate). Sprint 5B / BE-5B.3.
 *
 * <p>All operations run inside a tenant-bound transaction (Hibernate's
 * {@code @TenantId} discriminator filters {@code tenant_id} automatically).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeRecordServiceImpl implements GradeRecordService {

    private static final BigDecimal SCORE_MIN = new BigDecimal("0.00");
    private static final BigDecimal SCORE_MAX = new BigDecimal("20.00");

    private static final Set<String> LITERAL_AD_VALUES = Set.of("AD", "A");
    private static final Set<String> LITERAL_NA_VALUES = Set.of("NA", "A");
    private static final Set<String> LITERAL_A_B_C_D_VALUES = Set.of("A", "B", "C", "D");

    private final GradeRecordRepository gradeRecordRepository;
    private final EvaluationRepository evaluationRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final GradeRecordMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    // -------------------------------------------------------------------
    // Read endpoints
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<GradeRecordListItem> listGrades(
            UUID evaluationPublicUuid, GradeRecordFilters filters) {

        loadEvaluation(evaluationPublicUuid);

        GradeRecordFilters effective = (filters == null)
                ? new GradeRecordFilters(null, null, null)
                : filters;

        List<GradeRecord> rows = gradeRecordRepository.findFilteredByEvaluation(
                evaluationPublicUuid,
                effective.studentPublicUuid(),
                effective.sectionPublicUuid(),
                effective.isActive());

        return rows.stream().map(mapper::toListItem).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GradeRecordResponse getGrade(UUID publicUuid) {
        return mapper.toResponse(loadGrade(publicUuid));
    }

    // -------------------------------------------------------------------
    // Single upsert
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public GradeRecordResponse upsertGrade(
            UUID evaluationPublicUuid, CreateGradeRecordRequest request) {

        Evaluation evaluation = loadEvaluation(evaluationPublicUuid);
        ensureWritable(evaluation);

        Student student = loadStudent(parseUuid(request.studentPublicUuid()));
        ensureStudentEnrolled(evaluation, student);

        validatePayloadShape(evaluation.getScale(), request);

        GradeRecord saved = persistUpsert(evaluation, student, request);
        log.info("[grade-records] upsert -- publicUuid={} evaluation={} student={}",
                saved.getPublicUuid(), evaluation.getPublicUuid(), student.getPublicUuid());
        return mapper.toResponse(saved);
    }

    // -------------------------------------------------------------------
    // Update by publicUuid
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public GradeRecordResponse updateGrade(
            UUID publicUuid, UpdateGradeRecordRequest request) {

        if (request == null || request.isEmpty()) {
            throw new BadRequestException("EMPTY_PATCH",
                    "Update payload must contain at least one field");
        }

        GradeRecord grade = loadGrade(publicUuid);
        Evaluation evaluation = grade.getEvaluation();
        ensureWritable(evaluation);

        BigDecimal newScore = request.score() != null ? request.score() : grade.getScore();
        String newLiteral = request.literal() != null ? request.literal() : grade.getLiteral();
        String newComments = request.comments() != null ? request.comments() : grade.getComments();

        validateValuePresent(newScore, newLiteral);
        validateValueShape(evaluation.getScale(), newScore, newLiteral);

        grade.setScore(newScore);
        grade.setLiteral(newLiteral);
        grade.setComments(newComments);
        grade.setRecordedAt(Instant.now());
        grade.setRecordedByUserId(currentUserId());

        GradeRecord saved = gradeRecordRepository.saveAndFlush(grade);
        log.info("[grade-records] updated -- publicUuid={} evaluation={}",
                saved.getPublicUuid(), evaluation.getPublicUuid());
        return mapper.toResponse(saved);
    }

    // -------------------------------------------------------------------
    // Bulk upsert (atomic)
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public BulkGradeRecordResponse bulkUpsert(
            UUID evaluationPublicUuid, BulkGradeRecordRequest request) {

        Evaluation evaluation = loadEvaluation(evaluationPublicUuid);
        ensureWritable(evaluation);

        List<CreateGradeRecordRequest> rows = request.records();

        // Validate every row up-front so we never partially persist.
        // We resolve students in this pass to avoid issuing extra queries
        // during the persistence loop and to surface 404s before any write.
        List<Student> students = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            CreateGradeRecordRequest row = rows.get(i);
            try {
                Student student = loadStudent(parseUuid(row.studentPublicUuid()));
                ensureStudentEnrolled(evaluation, student);
                validatePayloadShape(evaluation.getScale(), row);
                students.add(student);
            }
            catch (BadRequestException | ConflictException ex) {
                throw new ConflictException(GradeRecordErrorCodes.GRADE_BULK_INVALID_ROW,
                        "Row " + i + " is invalid: " + ex.getMessage(), ex);
            }
        }

        int created = 0;
        int updated = 0;
        List<GradeRecordResponse> persisted = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            CreateGradeRecordRequest row = rows.get(i);
            Student student = students.get(i);

            Optional<GradeRecord> existing =
                    gradeRecordRepository.findByEvaluationAndStudent(evaluation, student);
            if (existing.isPresent()) {
                updated++;
            }
            else {
                created++;
            }
            GradeRecord saved = persistUpsert(evaluation, student, row);
            persisted.add(mapper.toResponse(saved));
        }

        log.info("[grade-records] bulk -- evaluation={} requested={} created={} updated={}",
                evaluation.getPublicUuid(), rows.size(), created, updated);
        return new BulkGradeRecordResponse(rows.size(), created, updated, persisted);
    }

    // -------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void deleteGrade(UUID publicUuid) {
        GradeRecord grade = loadGrade(publicUuid);
        // Lifecycle gate also applies to deletes: a grade attached to a
        // CLOSED evaluation is part of the historical academic record
        // and cannot be deleted (ADR-5B.7 + audit-friendliness).
        ensureWritable(grade.getEvaluation());
        gradeRecordRepository.delete(grade);
        log.info("[grade-records] deleted -- publicUuid={}", publicUuid);
    }

    // -------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------

    private GradeRecord persistUpsert(
            Evaluation evaluation, Student student, CreateGradeRecordRequest request) {

        GradeRecord grade = gradeRecordRepository
                .findByEvaluationAndStudent(evaluation, student)
                .orElseGet(() -> {
                    GradeRecord fresh = new GradeRecord();
                    fresh.setEvaluation(evaluation);
                    fresh.setStudent(student);
                    return fresh;
                });

        grade.setScore(normalizeScore(evaluation.getScale(), request.score()));
        grade.setLiteral(normalizeLiteral(evaluation.getScale(), request.literal()));
        grade.setComments(blankToNull(request.comments()));
        grade.setRecordedAt(Instant.now());
        grade.setRecordedByUserId(currentUserId());
        if (grade.getIsActive() == null) {
            grade.setIsActive(Boolean.TRUE);
        }

        return gradeRecordRepository.saveAndFlush(grade);
    }

    // -------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------

    private static void validatePayloadShape(
            EvaluationScale scale, CreateGradeRecordRequest request) {
        validateValuePresent(request.score(), request.literal());
        validateValueShape(scale, request.score(), request.literal());
    }

    private static void validateValuePresent(BigDecimal score, String literal) {
        boolean hasScore = score != null;
        boolean hasLiteral = literal != null && !literal.isBlank();
        if (!hasScore && !hasLiteral) {
            throw new BadRequestException(GradeRecordErrorCodes.GRADE_VALUE_REQUIRED,
                    "Either 'score' or 'literal' must be provided");
        }
    }

    private static void validateValueShape(
            EvaluationScale scale, BigDecimal score, String literal) {
        switch (scale) {
            case SCORE_0_20 -> validateScore(score, literal);
            case LITERAL_AD -> validateLiteral(scale, literal, score, LITERAL_AD_VALUES);
            case LITERAL_NA -> validateLiteral(scale, literal, score, LITERAL_NA_VALUES);
            case LITERAL_A_B_C_D ->
                    validateLiteral(scale, literal, score, LITERAL_A_B_C_D_VALUES);
        }
    }

    private static void validateScore(BigDecimal score, String literal) {
        if (literal != null && !literal.isBlank()) {
            throw new BadRequestException(
                    GradeRecordErrorCodes.GRADE_VALUE_SHAPE_MISMATCH,
                    "SCORE_0_20 evaluations only accept 'score'; remove 'literal'");
        }
        if (score == null) {
            throw new BadRequestException(GradeRecordErrorCodes.GRADE_VALUE_REQUIRED,
                    "SCORE_0_20 evaluations require 'score'");
        }
        if (score.compareTo(SCORE_MIN) < 0 || score.compareTo(SCORE_MAX) > 0) {
            throw new BadRequestException(
                    GradeRecordErrorCodes.GRADE_SCORE_OUT_OF_RANGE,
                    "score " + score + " is out of range [0.00, 20.00]");
        }
    }

    private static void validateLiteral(
            EvaluationScale scale, String literal, BigDecimal score,
            Set<String> allowed) {
        if (score != null) {
            throw new BadRequestException(
                    GradeRecordErrorCodes.GRADE_VALUE_SHAPE_MISMATCH,
                    scale + " evaluations only accept 'literal'; remove 'score'");
        }
        if (literal == null || literal.isBlank()) {
            throw new BadRequestException(GradeRecordErrorCodes.GRADE_VALUE_REQUIRED,
                    scale + " evaluations require 'literal'");
        }
        if (!allowed.contains(literal)) {
            throw new BadRequestException(
                    GradeRecordErrorCodes.GRADE_LITERAL_INVALID,
                    "literal '" + literal + "' is not allowed for scale "
                            + scale + "; allowed values: " + allowed);
        }
    }

    private static BigDecimal normalizeScore(EvaluationScale scale, BigDecimal score) {
        return scale == EvaluationScale.SCORE_0_20 ? score : null;
    }

    private static String normalizeLiteral(EvaluationScale scale, String literal) {
        if (scale == EvaluationScale.SCORE_0_20) {
            return null;
        }
        return blankToNull(literal);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        return value.isBlank() ? null : value;
    }

    private static void ensureWritable(Evaluation evaluation) {
        if (evaluation.getStatus() == EvaluationStatus.CLOSED) {
            throw new ConflictException(GradeRecordErrorCodes.GRADE_EVAL_CLOSED,
                    "Evaluation " + evaluation.getPublicUuid()
                            + " is CLOSED; grade writes are rejected");
        }
    }

    private void ensureStudentEnrolled(Evaluation evaluation, Student student) {
        Section section = evaluation.getTeacherAssignment().getSection();
        boolean enrolled = enrollmentRepository.existsActiveAt(
                student, section, evaluation.getScheduledDate());
        if (!enrolled) {
            throw new ConflictException(
                    GradeRecordErrorCodes.GRADE_STUDENT_NOT_ENROLLED,
                    "Student " + student.getPublicUuid()
                            + " is not enrolled in section "
                            + section.getPublicUuid()
                            + " on " + evaluation.getScheduledDate());
        }
    }

    // -------------------------------------------------------------------
    // Lookup helpers
    // -------------------------------------------------------------------

    private Evaluation loadEvaluation(UUID publicUuid) {
        return evaluationRepository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Evaluation", publicUuid));
    }

    private GradeRecord loadGrade(UUID publicUuid) {
        return gradeRecordRepository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "GradeRecord", publicUuid));
    }

    private Student loadStudent(UUID publicUuid) {
        return studentRepository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student", publicUuid));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        }
        catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_UUID",
                    "Invalid UUID: " + value);
        }
    }

    private UUID currentUserId() {
        return currentUserProvider.currentUserId().orElseThrow(
                () -> new UnauthorizedException(
                        "Authenticated user is required to record grades"));
    }
}
