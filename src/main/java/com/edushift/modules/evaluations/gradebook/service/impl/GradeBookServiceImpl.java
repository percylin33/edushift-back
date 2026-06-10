package com.edushift.modules.evaluations.gradebook.service.impl;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCellEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookEvaluationEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookStudentEntry;
import com.edushift.modules.evaluations.gradebook.service.GradeBookService;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link GradeBookService}
 * (Sprint 5B / BE-5B.4 / ADR-5B.9).
 *
 * <h3>Query plan</h3>
 * The aggregate makes exactly four DB round-trips, each tenant-scoped
 * by Hibernate's {@code @TenantId} filter:
 * <ol>
 *   <li>Load the {@code TeacherAssignment} by public UUID — 404 if
 *       unknown / cross-tenant.</li>
 *   <li>Load the section's active enrollments
 *       ({@code findActiveBySection}) — yields the student rows.</li>
 *   <li>Load the assignment's evaluations
 *       ({@code findAllByAssignment}) — yields the evaluation
 *       columns. Includes DRAFT for visibility (the FE shows them
 *       greyed out) but the weighted-average loop ignores them.</li>
 *   <li>Load every grade pointing at any of those evaluations
 *       ({@code findAllByAssignment} on the GradeRecord repo) —
 *       yields the cells. We deliberately do not page; sections cap
 *       at ~50 students × ~30 evaluations = 1.5k cells worst case
 *       (DEBT-EVAL-N tracks paging if a tenant exceeds this).</li>
 * </ol>
 *
 * <p>Everything else is in-memory work.</p>
 *
 * <h3>Weighted average algorithm</h3>
 * Per-student loop, ignoring evaluations that are not (a) PUBLISHED
 * or CLOSED and (b) numeric ({@code SCORE_0_20}). Standard weighted
 * mean; if the denominator (sum of weights of contributing
 * evaluations) is zero — including the all-RUBRIC case from
 * ADR-5B.4 — the result is {@code null}. The numerator is rounded to
 * 2 decimals (HALF_UP) so the FE renders 14.25 instead of
 * 14.249999...
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeBookServiceImpl implements GradeBookService {

    private static final int AVERAGE_SCALE = 2;

    private final TeacherAssignmentRepository assignmentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final EvaluationRepository evaluationRepository;
    private final GradeRecordRepository gradeRecordRepository;

    @Override
    @Transactional(readOnly = true)
    public GradeBookResponse buildGradeBook(UUID teacherAssignmentPublicUuid) {
        TeacherAssignment assignment = assignmentRepository
                .findByPublicUuid(teacherAssignmentPublicUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TeacherAssignment", teacherAssignmentPublicUuid));

        List<StudentEnrollment> enrollments = enrollmentRepository
                .findActiveBySection(assignment.getSection());
        List<Evaluation> evaluations = evaluationRepository
                .findAllByAssignment(assignment);
        List<GradeRecord> grades = gradeRecordRepository
                .findAllByAssignment(assignment);

        // Index grades by (evaluation_id, student_id) for O(1) lookup
        // during the weighted-average pass.
        Map<GradeKey, GradeRecord> gradeByKey = new HashMap<>(grades.size());
        for (GradeRecord g : grades) {
            gradeByKey.put(
                    new GradeKey(g.getEvaluation().getId(), g.getStudent().getId()),
                    g);
        }

        List<GradeBookEvaluationEntry> evalEntries = new ArrayList<>(evaluations.size());
        for (Evaluation e : evaluations) {
            evalEntries.add(new GradeBookEvaluationEntry(
                    e.getPublicUuid(),
                    e.getName(),
                    e.getKind(),
                    e.getScale(),
                    e.getStatus(),
                    e.getWeight(),
                    e.getScheduledDate()));
        }

        List<GradeBookStudentEntry> studentEntries = new ArrayList<>(enrollments.size());
        for (StudentEnrollment enrollment : enrollments) {
            Student student = enrollment.getStudent();
            BigDecimal weightedAverage = computeWeightedAverage(
                    student, evaluations, gradeByKey);
            studentEntries.add(new GradeBookStudentEntry(
                    student.getPublicUuid(),
                    fullName(student),
                    weightedAverage));
        }

        List<GradeBookCellEntry> cellEntries = new ArrayList<>(grades.size());
        for (GradeRecord g : grades) {
            cellEntries.add(new GradeBookCellEntry(
                    g.getStudent().getPublicUuid(),
                    g.getEvaluation().getPublicUuid(),
                    g.getScore(),
                    g.getLiteral(),
                    g.getRecordedAt()));
        }

        log.info("[gradebook] built -- assignment={} students={} evaluations={} cells={}",
                assignment.getPublicUuid(),
                studentEntries.size(),
                evalEntries.size(),
                cellEntries.size());

        return new GradeBookResponse(
                assignment.getPublicUuid(),
                assignment.getSection().getPublicUuid(),
                assignment.getSection().getName(),
                assignment.getCourse().getPublicUuid(),
                assignment.getCourse().getName(),
                studentEntries,
                evalEntries,
                cellEntries);
    }

    // =========================================================================
    // Weighted average
    // =========================================================================

    /**
     * Sum-of(score × weight) / sum-of(weight) over the evaluations
     * that qualify per ADR-5B.4: PUBLISHED or CLOSED, numeric scale
     * ({@code SCORE_0_20}), with a recorded score for this student.
     *
     * @return the average rounded to 2 decimals (HALF_UP), or
     *         {@code null} when no evaluation qualifies (e.g. the
     *         student has no scored numeric evaluation yet, or every
     *         evaluation is literal-scale)
     */
    private static BigDecimal computeWeightedAverage(
            Student student,
            List<Evaluation> evaluations,
            Map<GradeKey, GradeRecord> gradeByKey) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (Evaluation eval : evaluations) {
            if (!isContributingStatus(eval.getStatus())) continue;
            if (eval.getScale() != EvaluationScale.SCORE_0_20) continue;
            GradeRecord cell = gradeByKey.get(
                    new GradeKey(eval.getId(), student.getId()));
            if (cell == null || cell.getScore() == null) continue;
            BigDecimal weight = Objects.requireNonNullElse(
                    eval.getWeight(), BigDecimal.ZERO);
            numerator = numerator.add(cell.getScore().multiply(weight));
            denominator = denominator.add(weight);
        }
        if (denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, AVERAGE_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean isContributingStatus(EvaluationStatus status) {
        return status == EvaluationStatus.PUBLISHED
                || status == EvaluationStatus.CLOSED;
    }

    private static String fullName(Student student) {
        StringBuilder sb = new StringBuilder();
        if (student.getFirstName() != null) sb.append(student.getFirstName());
        if (student.getLastName() != null) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(student.getLastName());
        }
        if (student.getSecondLastName() != null
                && !student.getSecondLastName().isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(student.getSecondLastName());
        }
        return sb.toString();
    }

    /**
     * Composite key for the in-memory grade index. Internal-only —
     * not exposed via DTOs.
     */
    private record GradeKey(UUID evaluationId, UUID studentId) {
    }
}
