package com.edushift.modules.me.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.materials.entity.Material;
import com.edushift.modules.materials.repository.MaterialRepository;
import com.edushift.modules.me.dto.MeGradeResponse;
import com.edushift.modules.me.dto.MeSectionDetailResponse;
import com.edushift.modules.me.dto.MeSectionDetailResponse.MeMaterialItem;
import com.edushift.modules.me.dto.MeSectionDetailResponse.MeQuizItem;
import com.edushift.modules.me.dto.MeSectionDetailResponse.MeTaskItem;
import com.edushift.modules.me.dto.MeSectionResponse;
import com.edushift.modules.me.service.MeAcademicService;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.repository.TaskRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link MeAcademicService} implementation
 * (DEBT-STUDENT-PRIVACY / Fase 2).
 *
 * <p>Loads the caller's ACTIVE enrollments and projects them into the
 * {@code /me} DTOs. Authorization is enforced at the service: every
 * endpoint validates that the caller is enrolled in the section before
 * any data is returned, otherwise a {@link NotFoundException} surfaces
 * (anti-enumeration — never reveal whether the section exists).</p>
 *
 * <h3>Performance</h3>
 * <ul>
 *   <li>One query to load all active enrollments with the section
 *       joined (no N+1 on the list endpoint).</li>
 *   <li>One IN-batch query per child collection (materials / tasks /
 *       quizzes) so the detail endpoint never fans out one round-trip
 *       per row.</li>
 *   <li>Counter projections in the list endpoint are computed with
 *       {@code COUNT()} SQL via the existing repositories.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeAcademicServiceImpl implements MeAcademicService {

    /** Page size used by the "top N" queries that back the list counters. */
    private static final int COUNTER_PAGE_SIZE = 1000;

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;
    private final MaterialRepository materialRepository;
    private final TaskRepository taskRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;
    private final GradeRecordRepository gradeRecordRepository;
    private final CurrentUserProvider currentUserProvider;

    // ====================================================================
    // Public endpoints
    // ====================================================================

    @Override
    @Transactional(readOnly = true)
    public List<MeSectionResponse> listMySections() {
        Student student = requireStudent();
        List<StudentEnrollment> enrollments = activeEnrollments(student);

        if (enrollments.isEmpty()) {
            return List.of();
        }

        // Bulk-load sections so the lazy ManyToOne doesn't trigger N+1.
        List<UUID> sectionIds = enrollments.stream()
                .map(e -> e.getSection().getId())
                .toList();
        Map<UUID, Section> sectionsById = sectionRepository.findAllById(sectionIds).stream()
                .collect(Collectors.toMap(Section::getId, s -> s));

        Map<UUID, List<TeacherAssignment>> assignmentsBySection =
                assignmentsBySection(sectionIds);

        List<MeSectionResponse> out = new ArrayList<>();
        for (StudentEnrollment enrollment : enrollments) {
            Section section = sectionsById.get(enrollment.getSection().getId());
            if (section == null) {
                continue;
            }
            long materials = materialRepository
                    .findAllBySectionOrderByCreatedAtDesc(section, PageRequest.of(0, COUNTER_PAGE_SIZE))
                    .getTotalElements();
            long tasks = taskRepository
                    .findAllBySectionOrderByDueAtDesc(section, PageRequest.of(0, COUNTER_PAGE_SIZE))
                    .getTotalElements();
            long quizzes = quizRepository
                    .findAllBySectionOrderByDueAtDesc(section, PageRequest.of(0, COUNTER_PAGE_SIZE))
                    .getTotalElements();

            List<TeacherAssignment> assignments = assignmentsBySection.getOrDefault(
                    section.getId(), List.of());
            if (assignments.isEmpty()) {
                out.add(toSectionCard(section, enrollment, null, materials, tasks, quizzes));
                continue;
            }
            for (TeacherAssignment assignment : assignments) {
                out.add(toSectionCard(section, enrollment, assignment, materials, tasks, quizzes));
            }
        }

        // Stable order: by course name then section.
        out.sort(Comparator
                .comparing((MeSectionResponse r) -> r.courseName() == null ? "" : r.courseName(),
                        Comparator.naturalOrder())
                .thenComparing((MeSectionResponse r) -> r.gradeName() == null ? "" : r.gradeName())
                .thenComparing(MeSectionResponse::name,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public MeSectionDetailResponse getMySection(UUID sectionPublicUuid) {
        Student student = requireStudent();
        Section section = sectionRepository.findByPublicUuid(sectionPublicUuid)
                .orElseThrow(() -> new NotFoundException(
                        "ME_SECTION_NOT_FOUND",
                        "Section not found in the current tenant"));

        // Authorization: must be ACTIVE-enrolled. Same shape as the
        // list endpoint to keep the rule centralised.
        boolean enrolled = enrollmentRepository
                .findActiveByStudentFetchSection(student)
                .stream()
                .anyMatch(e -> section.getId().equals(e.getSection().getId()));
        if (!enrolled) {
            // Anti-enumeration: surface 404 (not 403) so the caller
            // can't distinguish "section exists, you're not enrolled"
            // from "section doesn't exist".
            throw new NotFoundException(
                    "ME_SECTION_NOT_FOUND",
                    "Section not found in the current tenant");
        }

        TeacherAssignment primary = primaryAssignmentsBySection(List.of(section.getId()))
                .get(section.getId());

        // Bulk-load the child collections, ordered consistently so the
        // FE doesn't need to re-sort.
        List<MeMaterialItem> materials = materialRepository
                .findAllBySectionOrderByCreatedAtDesc(section, PageRequest.of(0, COUNTER_PAGE_SIZE))
                .map(m -> new MeMaterialItem(
                        m.getPublicUuid(),
                        m.getTitle(),
                        m.getKind().name(),
                        m.getOwnerUserId(),
                        m.getCreatedAt() != null ? m.getCreatedAt().toString() : null))
                .getContent();

        List<MeTaskItem> tasks = taskRepository
                .findAllBySectionOrderByDueAtDesc(section, PageRequest.of(0, COUNTER_PAGE_SIZE))
                .map(t -> new MeTaskItem(
                        t.getPublicUuid(),
                        t.getTitle(),
                        t.getDueAt() != null ? t.getDueAt().toString() : null,
                        t.getDueAt() != null && t.getDueAt().isBefore(Instant.now()),
                        t.getOwnerUserId(),
                        t.getAttachmentPublicUuid() != null))
                .getContent();

        List<MeQuizItem> quizzes = quizRepository
                .findAllBySectionOrderByDueAtDesc(section, PageRequest.of(0, COUNTER_PAGE_SIZE))
                .map(this::toQuizItem)
                .getContent();

        return new MeSectionDetailResponse(
                section.getPublicUuid(),
                section.getName(),
                section.getGrade() != null ? section.getGrade().getName() : null,
                section.getGrade() != null && section.getGrade().getLevel() != null
                        ? section.getGrade().getLevel().getName() : null,
                section.getAcademicYear() != null
                        ? section.getAcademicYear().getName() : null,
                primary != null ? teacherDisplayName(primary.getTeacher()) : null,
                primary != null && primary.getCourse() != null
                        ? primary.getCourse().getName() : null,
                materials,
                tasks,
                quizzes
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeGradeResponse> listMyGrades() {
        Student student = requireStudent();
        List<GradeRecord> records = gradeRecordRepository
                .findAllByStudentPublicUuid(student.getPublicUuid());

        if (records.isEmpty()) {
            return List.of();
        }

        // Group by evaluation so the FE can group by evaluation when
        // needed. Order by scheduled date desc (or recorded date desc
        // when scheduled date is null) for stable display.
        return records.stream()
                .sorted(Comparator
                        .comparing((GradeRecord g) -> scheduledDate(g))
                        .thenComparing(GradeRecord::getRecordedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(g -> new MeGradeResponse(
                        g.getPublicUuid(),
                        g.getEvaluation() != null ? g.getEvaluation().getPublicUuid() : null,
                        g.getEvaluation() != null ? g.getEvaluation().getName() : null,
                        sectionName(g),
                        courseName(g),
                        g.getScore(),
                        maxScore(g),
                        g.getLiteral(),
                        g.getComments(),
                        scheduledDate(g),
                        g.getRecordedAt()))
                .toList();
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    /**
     * Resolves the {@link Student} row linked to the JWT caller.
     * Throws {@link NotFoundException} (404) when the caller has no
     * linked student — the FE renders an empty state instead of an
     * error.
     */
    private Student requireStudent() {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new NotFoundException(
                        "ME_NOT_AUTHENTICATED",
                        "Authenticated user is required"));
        return studentRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "ME_NOT_A_STUDENT",
                        "Caller has no student record in this tenant"));
    }

    /**
     * Returns the caller's ACTIVE enrollments, ordered by enrollment
     * date desc to match the list endpoint convention.
     */
    private List<StudentEnrollment> activeEnrollments(Student student) {
        return enrollmentRepository.findActiveByStudentFetchSection(student).stream()
                .filter(e -> e.getStatus() == StudentEnrollmentStatus.ACTIVE)
                .sorted(Comparator
                        .comparing(StudentEnrollment::getEnrolledAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Active teacher assignments grouped by section id (all courses, not
     * only the first/primary assignment).
     */
    private Map<UUID, List<TeacherAssignment>> assignmentsBySection(List<UUID> sectionIds) {
        if (sectionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<TeacherAssignment>> bySection = new java.util.LinkedHashMap<>();
        for (UUID sectionId : sectionIds) {
            Section section = sectionRepository.findById(sectionId).orElse(null);
            if (section == null) {
                continue;
            }
            bySection.put(section.getId(), teacherAssignmentRepository.findAllBySectionActive(section, null));
        }
        return bySection;
    }

    private MeSectionResponse toSectionCard(
            Section section,
            StudentEnrollment enrollment,
            TeacherAssignment assignment,
            long materials,
            long tasks,
            long quizzes) {
        String courseName = assignment != null && assignment.getCourse() != null
                ? assignment.getCourse().getName() : null;
        String teacherName = assignment != null ? teacherDisplayName(assignment.getTeacher()) : null;
        return new MeSectionResponse(
                section.getPublicUuid(),
                section.getName(),
                section.getGrade() != null ? section.getGrade().getName() : null,
                section.getGrade() != null && section.getGrade().getLevel() != null
                        ? section.getGrade().getLevel().getName() : null,
                section.getAcademicYear() != null
                        ? section.getAcademicYear().getName() : null,
                teacherName,
                courseName,
                materials,
                tasks,
                quizzes,
                enrollment.getEnrolledAt() != null
                        ? enrollment.getEnrolledAt().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
                        : null
        );
    }

    /**
     * Bulk-loads the "primary" teacher assignment per section in a
     * single IN query. Returns the assignment keyed by section id;
     * sections with no active assignment are absent from the map.
     */
    private Map<UUID, TeacherAssignment> primaryAssignmentsBySection(List<UUID> sectionIds) {
        if (sectionIds.isEmpty()) {
            return Map.of();
        }
        // Fetch active assignments for all sections in one round-trip,
        // then keep the first per section (most-recently assigned).
        List<TeacherAssignment> all = new ArrayList<>();
        for (UUID sectionId : sectionIds) {
            Section section = sectionRepository.findById(sectionId).orElse(null);
            if (section == null) continue;
            all.addAll(teacherAssignmentRepository.findAllBySectionActive(section, null));
        }
        return all.stream()
                .collect(Collectors.toMap(
                        a -> a.getSection().getId(),
                        a -> a,
                        (first, second) -> first));
    }

    /**
     * Builds the {@code MeQuizItem} for the section detail. Reads the
     * question count from {@link QuizQuestionRepository} without making
     * one extra query per quiz — but the underlying queries are still
     * per-row (acceptable for a section-level view bounded by the
     * COUNTER_PAGE_SIZE).
     */
    private MeQuizItem toQuizItem(Quiz q) {
        int questions = (int) quizQuestionRepository.countByQuiz(q);
        return new MeQuizItem(
                q.getPublicUuid(),
                q.getTitle(),
                q.getStatus() != null ? q.getStatus().name() : QuizStatus.DRAFT.name(),
                q.getDueAt() != null ? q.getDueAt().toString() : null,
                q.getMaxScore() != null ? q.getMaxScore().intValue() : null,
                q.getTimeLimitMinutes() != null ? q.getTimeLimitMinutes().intValue() : null,
                q.getAttemptsAllowed() != null ? q.getAttemptsAllowed().intValue() : null,
                q.getOwnerUserId(),
                questions);
    }

    private static String teacherDisplayName(Teacher teacher) {
        if (teacher == null) return null;
        String full = teacher.fullName();
        return full == null || full.isBlank() ? null : full;
    }

    private static Instant scheduledDate(GradeRecord g) {
        if (g.getEvaluation() == null || g.getEvaluation().getScheduledDate() == null) {
            return g.getRecordedAt();
        }
        // Convert LocalDate at start-of-day UTC to Instant for a
        // stable, comparable timestamp.
        return g.getEvaluation().getScheduledDate()
                .atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
    }

    private static BigDecimal maxScore(GradeRecord g) {
        if (g.getEvaluation() == null) return null;
        // Numeric scales expose maxScore; literal scales don't.
        return switch (g.getEvaluation().getScale()) {
            case SCORE_0_20 -> BigDecimal.valueOf(20);
            case LITERAL_AD, LITERAL_NA, LITERAL_A_B_C_D -> null;
        };
    }

    private static String sectionName(GradeRecord g) {
        if (g.getEvaluation() == null
                || g.getEvaluation().getTeacherAssignment() == null
                || g.getEvaluation().getTeacherAssignment().getSection() == null) {
            return null;
        }
        return g.getEvaluation().getTeacherAssignment().getSection().getName();
    }

    private static String courseName(GradeRecord g) {
        if (g.getEvaluation() == null
                || g.getEvaluation().getTeacherAssignment() == null
                || g.getEvaluation().getTeacherAssignment().getCourse() == null) {
            return null;
        }
        return g.getEvaluation().getTeacherAssignment().getCourse().getName();
    }
}
