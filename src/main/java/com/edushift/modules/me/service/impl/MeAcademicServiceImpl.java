package com.edushift.modules.me.service.impl;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.period.support.AcademicPeriodResolver;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.gradebook.CnebLiteralScale;
import com.edushift.modules.evaluations.gradebook.GradeAverageCalculator;
import com.edushift.modules.evaluations.gradebook.GradeAverageCalculator.GradeKey;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCategoryEntry;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.graderegister.dto.GradeCategoryResponse;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateResponse;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterTemplateService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.materials.entity.Material;
import com.edushift.modules.materials.repository.MaterialRepository;
import com.edushift.modules.me.dto.MeGradeResponse;
import com.edushift.modules.me.dto.MeGradesSummaryResponse;
import com.edushift.modules.me.dto.MeGradesSummaryResponse.Category;
import com.edushift.modules.me.dto.MeGradesSummaryResponse.Course;
import com.edushift.modules.me.dto.MeGradesSummaryResponse.Item;
import com.edushift.modules.me.dto.MeSectionDetailResponse;
import com.edushift.modules.me.dto.MeSectionDetailResponse.MeMaterialItem;
import com.edushift.modules.me.dto.MeSectionDetailResponse.MeQuizItem;
import com.edushift.modules.me.dto.MeSectionDetailResponse.MeTaskItem;
import com.edushift.modules.me.dto.MeSectionPeriodResponse;
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
import com.edushift.modules.tasks.entity.TaskStatus;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final EvaluationRepository evaluationRepository;
    private final GradeRegisterTemplateService gradeRegisterTemplateService;
    private final SubmissionRepository submissionRepository;
    private final AcademicPeriodResolver academicPeriodResolver;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final CurrentUserProvider currentUserProvider;
    private final FileObjectService fileObjectService;

    // ====================================================================
    // Public endpoints
    // ====================================================================

    @Override
    @Transactional(readOnly = true)
    public List<MeSectionResponse> listMySections(UUID periodPublicUuid) {
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
        Optional<AcademicPeriod> currentPeriod = academicPeriodResolver.resolveCurrentPeriod();
        Optional<AcademicPeriod> selectedPeriod = resolveSelectedPeriod(periodPublicUuid, currentPeriod);

        List<MeSectionResponse> out = new ArrayList<>();
        for (StudentEnrollment enrollment : enrollments) {
            Section section = sectionsById.get(enrollment.getSection().getId());
            if (section == null) {
                continue;
            }

            List<TeacherAssignment> assignments = filterAssignmentsForPeriod(
                    assignmentsBySection.getOrDefault(section.getId(), List.of()),
                    selectedPeriod);
            if (assignments.isEmpty()) {
                if (selectedPeriod.isPresent()) {
                    continue;
                }
                out.add(toSectionCard(section, enrollment, null, 0, 0, 0));
                continue;
            }
            for (TeacherAssignment assignment : assignments) {
                long materials = materialRepository
                        .countBySectionAndTeacherAssignment(section, assignment);
                long tasks = taskRepository
                        .countBySectionAndTeacherAssignmentAndStatus(
                                section, assignment, TaskStatus.PUBLISHED);
                long quizzes = quizRepository
                        .countBySectionAndTeacherAssignmentAndStatus(
                                section, assignment, QuizStatus.PUBLISHED);
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
    public List<MeSectionPeriodResponse> listMySectionPeriods() {
        Student student = requireStudent();
        List<StudentEnrollment> enrollments = activeEnrollments(student);
        if (enrollments.isEmpty()) {
            return List.of();
        }

        List<UUID> sectionIds = enrollments.stream()
                .map(e -> e.getSection().getId())
                .toList();
        Map<UUID, List<TeacherAssignment>> assignmentsBySection =
                assignmentsBySection(sectionIds);
        Optional<AcademicPeriod> currentPeriod = academicPeriodResolver.resolveCurrentPeriod();
        UUID currentPeriodId = currentPeriod.map(AcademicPeriod::getId).orElse(null);

        Map<UUID, AcademicPeriod> distinct = new LinkedHashMap<>();
        for (List<TeacherAssignment> assignments : assignmentsBySection.values()) {
            for (TeacherAssignment assignment : assignments) {
                AcademicPeriod period = assignment.getAcademicPeriod();
                if (period != null) {
                    distinct.putIfAbsent(period.getId(), period);
                }
            }
        }

        return distinct.values().stream()
                .sorted(Comparator
                        .comparing(AcademicPeriod::getOrdinal, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(period -> new MeSectionPeriodResponse(
                        period.getPublicUuid(),
                        period.getName(),
                        period.getOrdinal() != null ? period.getOrdinal() : 0,
                        period.getId().equals(currentPeriodId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MeSectionDetailResponse getMySection(UUID sectionPublicUuid, UUID assignmentPublicUuid) {
        Student student = requireStudent();
        Section section = sectionRepository.findByPublicUuid(sectionPublicUuid)
                .orElseThrow(() -> new NotFoundException(
                        "ME_SECTION_NOT_FOUND",
                        "Section not found in the current tenant"));

        boolean enrolled = enrollmentRepository
                .findActiveByStudentFetchSection(student)
                .stream()
                .anyMatch(e -> section.getId().equals(e.getSection().getId()));
        if (!enrolled) {
            throw new NotFoundException(
                    "ME_SECTION_NOT_FOUND",
                    "Section not found in the current tenant");
        }

        TeacherAssignment assignment = resolveAssignment(section, assignmentPublicUuid);

        PageRequest page = PageRequest.of(0, COUNTER_PAGE_SIZE);
        List<MeMaterialItem> materials;
        List<MeTaskItem> tasks;
        List<MeQuizItem> quizzes;

        if (assignment != null) {
            materials = materialRepository
                    .findAllBySectionAndTeacherAssignmentOrderByCreatedAtDesc(
                            section, assignment, page)
                    .map(m -> new MeMaterialItem(
                            m.getPublicUuid(),
                            m.getTitle(),
                            m.getKind().name(),
                            m.getOwnerUserId(),
                            m.getCreatedAt() != null ? m.getCreatedAt().toString() : null,
                            m.getFilePublicUuid(),
                            resolveMaterialFilename(m),
                            m.getKind() == com.edushift.modules.materials.entity.MaterialKind.VIDEO_LINK
                                    ? m.getExternalUrl() : null))
                    .getContent();
            tasks = taskRepository
                    .findAllBySectionAndTeacherAssignmentAndStatusOrderByDueAtDesc(
                            section, assignment, TaskStatus.PUBLISHED, page)
                    .map(t -> new MeTaskItem(
                            t.getPublicUuid(),
                            t.getTitle(),
                            t.getDueAt() != null ? t.getDueAt().toString() : null,
                            t.getDueAt() != null && t.getDueAt().isBefore(Instant.now()),
                            t.getOwnerUserId(),
                            t.getAttachmentPublicUuid() != null))
                    .getContent();
            quizzes = quizRepository
                    .findAllBySectionAndTeacherAssignmentAndStatusOrderByDueAtDesc(
                            section, assignment, QuizStatus.PUBLISHED, page)
                    .map(this::toQuizItem)
                    .getContent();
        } else {
            // Legacy fallback: no assignment filter — still hide drafts.
            materials = materialRepository
                    .findAllBySectionOrderByCreatedAtDesc(section, page)
                    .map(m -> new MeMaterialItem(
                            m.getPublicUuid(),
                            m.getTitle(),
                            m.getKind().name(),
                            m.getOwnerUserId(),
                            m.getCreatedAt() != null ? m.getCreatedAt().toString() : null,
                            m.getFilePublicUuid(),
                            resolveMaterialFilename(m),
                            m.getKind() == com.edushift.modules.materials.entity.MaterialKind.VIDEO_LINK
                                    ? m.getExternalUrl() : null))
                    .getContent();
            tasks = taskRepository
                    .findAllBySectionAndStatusOrderByDueAtDesc(
                            section, TaskStatus.PUBLISHED, page)
                    .map(t -> new MeTaskItem(
                            t.getPublicUuid(),
                            t.getTitle(),
                            t.getDueAt() != null ? t.getDueAt().toString() : null,
                            t.getDueAt() != null && t.getDueAt().isBefore(Instant.now()),
                            t.getOwnerUserId(),
                            t.getAttachmentPublicUuid() != null))
                    .getContent();
            quizzes = quizRepository
                    .findAllBySectionAndStatusOrderByDueAtDesc(
                            section, QuizStatus.PUBLISHED, page)
                    .map(this::toQuizItem)
                    .getContent();
        }

        return new MeSectionDetailResponse(
                section.getPublicUuid(),
                section.getName(),
                section.getGrade() != null ? section.getGrade().getName() : null,
                section.getGrade() != null && section.getGrade().getLevel() != null
                        ? section.getGrade().getLevel().getName() : null,
                section.getAcademicYear() != null
                        ? section.getAcademicYear().getName() : null,
                assignment != null ? teacherDisplayName(assignment.getTeacher()) : null,
                assignment != null && assignment.getCourse() != null
                        ? assignment.getCourse().getName() : null,
                materials,
                tasks,
                quizzes,
                assignment != null ? assignment.getPublicUuid() : null,
                periodLabel(assignment, academicPeriodResolver.resolveCurrentPeriod().orElse(null))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeGradeResponse> listMyGrades() {
        Student student = requireStudent();
        List<GradeRecord> records = gradeRecordRepository
                .findAllByStudentPublicUuid(student.getPublicUuid());
        List<Submission> gradedTasks = student.getUserId() == null
                ? List.of()
                : submissionRepository.findAllByStudentUserIdAndStatus(
                        student.getUserId(), SubmissionStatus.GRADED);

        List<MeGradeResponse> out = new ArrayList<>(records.size() + gradedTasks.size());
        for (GradeRecord record : records) {
            out.add(toMeGrade(record));
        }
        for (Submission submission : gradedTasks) {
            MeGradeResponse mapped = toMeGrade(submission);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        out.sort(Comparator
                .comparing(MeGradeResponse::recordedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MeGradeResponse::scheduledDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public MeGradesSummaryResponse summarizeMyGrades(UUID periodPublicUuid) {
        Student student = requireStudent();
        Optional<AcademicPeriod> currentPeriod = academicPeriodResolver.resolveCurrentPeriod();
        Optional<AcademicPeriod> selectedPeriod = resolveSelectedPeriod(periodPublicUuid, currentPeriod);

        List<TeacherAssignment> assignments = assignmentsForPeriod(student, selectedPeriod);
        Map<GradeKey, GradeRecord> gradeByKey = gradesForStudent(student);
        Set<UUID> assignmentIds = assignments.stream()
                .map(TeacherAssignment::getId)
                .collect(Collectors.toSet());

        List<Course> courses = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        for (TeacherAssignment assignment : assignments) {
            appendAssignment(student, assignment, gradeByKey, courses, items);
        }
        appendGradedTasks(student, assignmentIds, items);

        courses.sort(Comparator.comparing(
                course -> course.courseName() == null ? "" : course.courseName(),
                String.CASE_INSENSITIVE_ORDER));
        items.sort(Comparator.comparing(
                Item::recordedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        return new MeGradesSummaryResponse(
                toPeriod(selectedPeriod, currentPeriod),
                meanOf(courses),
                courses.size(),
                (int) courses.stream()
                        .filter(course -> course.average() != null || course.literalAverage() != null)
                        .count(),
                items.size(),
                List.copyOf(courses),
                List.copyOf(items));
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
                        : null,
                assignment != null ? assignment.getPublicUuid() : null,
                periodLabel(assignment, null)
        );
    }

    private Optional<AcademicPeriod> resolveSelectedPeriod(
            UUID periodPublicUuid,
            Optional<AcademicPeriod> currentPeriod) {
        if (periodPublicUuid == null) {
            return currentPeriod;
        }
        return academicPeriodRepository.findByPublicUuid(periodPublicUuid)
                .map(Optional::of)
                .orElseThrow(() -> new NotFoundException(
                        "ME_PERIOD_NOT_FOUND",
                        "Academic period not found in the current tenant"));
    }

    private List<TeacherAssignment> filterAssignmentsForPeriod(
            List<TeacherAssignment> assignments,
            Optional<AcademicPeriod> selectedPeriod) {
        if (assignments.isEmpty() || selectedPeriod.isEmpty()) {
            return assignments;
        }
        UUID periodId = selectedPeriod.get().getId();
        return assignments.stream()
                .filter(a -> a.getAcademicPeriod() != null
                        && periodId.equals(a.getAcademicPeriod().getId()))
                .toList();
    }

    private List<TeacherAssignment> filterAssignmentsForCurrentPeriod(
            List<TeacherAssignment> assignments,
            Optional<AcademicPeriod> currentPeriod) {
        return filterAssignmentsForPeriod(assignments, currentPeriod);
    }

    private static String periodLabel(TeacherAssignment assignment, AcademicPeriod fallback) {
        if (assignment != null && assignment.getAcademicPeriod() != null) {
            return assignment.getAcademicPeriod().getName();
        }
        return fallback != null ? fallback.getName() : null;
    }

    /**
     * Resolves the assignment for a student detail view. When the UUID is
     * provided it must belong to the section; otherwise falls back to the
     * first active assignment (legacy clients).
     */
    private TeacherAssignment resolveAssignment(Section section, UUID assignmentPublicUuid) {
        List<TeacherAssignment> active =
                teacherAssignmentRepository.findAllBySectionActive(section, null);
        if (assignmentPublicUuid != null) {
            return active.stream()
                    .filter(a -> assignmentPublicUuid.equals(a.getPublicUuid()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "ME_SECTION_NOT_FOUND",
                            "Section not found in the current tenant"));
        }
        Optional<AcademicPeriod> currentPeriod = academicPeriodResolver.resolveCurrentPeriod();
        List<TeacherAssignment> inPeriod = filterAssignmentsForCurrentPeriod(active, currentPeriod);
        if (!inPeriod.isEmpty()) {
            return inPeriod.getFirst();
        }
        return active.isEmpty() ? null : active.getFirst();
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

    private static MeGradeResponse toMeGrade(GradeRecord g) {
        return new MeGradeResponse(
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
                g.getRecordedAt());
    }

    private static MeGradeResponse toMeGrade(Submission submission) {
        Task task = submission.getTask();
        if (task == null) {
            return null;
        }
        var assignment = task.getTeacherAssignment();
        String sectionName = task.getSection() != null ? task.getSection().getName() : null;
        String courseName = assignment != null && assignment.getCourse() != null
                ? assignment.getCourse().getName() : null;
        BigDecimal score = submission.getGrade() == null
                ? null : BigDecimal.valueOf(submission.getGrade().intValue());
        Instant recordedAt = submission.getGradedAt() != null
                ? submission.getGradedAt() : submission.getUpdatedAt();
        return new MeGradeResponse(
                submission.getPublicUuid(),
                task.getPublicUuid(),
                task.getTitle(),
                sectionName,
                courseName,
                score,
                BigDecimal.valueOf(task.getMaxScore()),
                null,
                submission.getFeedback(),
                task.getDueAt(),
                recordedAt);
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
            case LITERAL_AD, LITERAL_NA, LITERAL_A_B_C_D, LITERAL_CNEB -> null;
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

    private String resolveMaterialFilename(Material material) {
        if (material.getFilePublicUuid() == null) {
            return null;
        }
        return fileObjectService.findByPublicUuid(material.getFilePublicUuid())
                .map(fo -> fo.getOriginalName())
                .orElse(null);
    }

    private List<TeacherAssignment> assignmentsForPeriod(
            Student student,
            Optional<AcademicPeriod> selectedPeriod) {
        List<StudentEnrollment> enrollments = activeEnrollments(student);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        List<UUID> sectionIds = enrollments.stream()
                .map(enrollment -> enrollment.getSection().getId())
                .toList();
        return assignmentsBySection(sectionIds).values().stream()
                .flatMap(list -> filterAssignmentsForPeriod(list, selectedPeriod).stream())
                .toList();
    }

    private Map<GradeKey, GradeRecord> gradesForStudent(Student student) {
        Map<GradeKey, GradeRecord> gradeByKey = new HashMap<>();
        for (GradeRecord record : gradeRecordRepository.findAllByStudentPublicUuid(student.getPublicUuid())) {
            if (record.getEvaluation() == null || record.getStudent() == null) {
                continue;
            }
            if (!student.getId().equals(record.getStudent().getId())) {
                continue;
            }
            gradeByKey.put(
                    new GradeKey(record.getEvaluation().getId(), student.getId()),
                    record);
        }
        return gradeByKey;
    }

    private void appendAssignment(
            Student student,
            TeacherAssignment assignment,
            Map<GradeKey, GradeRecord> gradeByKey,
            List<Course> courses,
            List<Item> items) {
        List<Evaluation> evaluations = evaluationRepository.findAllByAssignment(assignment);
        Optional<GradeRegisterTemplateResponse> template =
                gradeRegisterTemplateService.resolveForAssignment(assignment);
        List<GradeCategoryResponse> templateCategories = template
                .map(GradeRegisterTemplateResponse::categories)
                .orElse(List.of());
        if (templateCategories == null) {
            templateCategories = List.of();
        }
        List<GradeBookCategoryEntry> categories = templateCategories.stream()
                .map(MeAcademicServiceImpl::toCategoryEntry)
                .toList();
        boolean literalSheet = template
                .map(value -> value.gradingScale() == EvaluationScale.LITERAL_CNEB)
                .orElse(false);
        GradeAverageCalculator.Row row = GradeAverageCalculator.forStudent(
                student.getId(), evaluations, gradeByKey, categories, literalSheet);

        String scale = template
                .map(value -> value.gradingScale() == null ? null : value.gradingScale().name())
                .orElse(null);
        if (scale == null && row.weightedAverage() != null) {
            scale = EvaluationScale.SCORE_0_20.name();
        }
        if (scale == null && row.literalAverage() != null) {
            scale = EvaluationScale.LITERAL_CNEB.name();
        }

        List<Category> categoryDtos = new ArrayList<>();
        for (GradeCategoryResponse category : templateCategories) {
            if (!category.contributesToAverage()) {
                continue;
            }
            categoryDtos.add(new Category(
                    category.publicUuid(),
                    category.name(),
                    category.weight(),
                    row.categoryAverages().get(category.publicUuid()),
                    row.categoryLiterals().get(category.publicUuid())));
        }

        courses.add(new Course(
                assignment.getPublicUuid(),
                assignment.getCourse() != null ? assignment.getCourse().getPublicUuid() : null,
                assignment.getCourse() != null ? assignment.getCourse().getName() : null,
                assignment.getSection() != null ? assignment.getSection().getName() : null,
                teacherDisplayName(assignment.getTeacher()),
                scale,
                row.weightedAverage(),
                row.literalAverage(),
                List.copyOf(categoryDtos)));

        for (Evaluation evaluation : evaluations) {
            GradeRecord cell = gradeByKey.get(new GradeKey(evaluation.getId(), student.getId()));
            if (cell == null || !GradeAverageCalculator.isContributingStatus(evaluation.getStatus())) {
                continue;
            }
            items.add(toRegisterItem(assignment, evaluation, cell, literalSheet, categories));
        }
    }

    private void appendGradedTasks(Student student, Set<UUID> assignmentIds, List<Item> items) {
        if (student.getUserId() == null || assignmentIds.isEmpty()) {
            return;
        }
        for (Submission submission : submissionRepository.findAllByStudentUserIdAndStatus(
                student.getUserId(), SubmissionStatus.GRADED)) {
            Item item = toTaskItem(submission, assignmentIds);
            if (item != null) {
                items.add(item);
            }
        }
    }

    private static Item toRegisterItem(
            TeacherAssignment assignment,
            Evaluation evaluation,
            GradeRecord cell,
            boolean literalSheet,
            List<GradeBookCategoryEntry> categories) {
        boolean official = countsTowardAverage(evaluation, cell, literalSheet, categories);
        return new Item(
                cell.getPublicUuid(),
                assignment.getPublicUuid(),
                assignment.getCourse() != null ? assignment.getCourse().getPublicUuid() : null,
                assignment.getCourse() != null ? assignment.getCourse().getName() : null,
                assignment.getSection() != null ? assignment.getSection().getName() : null,
                evaluation.getName(),
                evaluation.getKind() != null ? evaluation.getKind().name() : null,
                evaluation.getWeight(),
                evaluation.getScale() != null ? evaluation.getScale().name() : null,
                cell.getScore(),
                maxScore(cell),
                cell.getLiteral(),
                cell.getComments(),
                teacherDisplayName(assignment.getTeacher()),
                assignment.getAcademicPeriod() != null
                        ? assignment.getAcademicPeriod().getPublicUuid() : null,
                scheduledDate(cell),
                cell.getRecordedAt(),
                official,
                "REGISTER",
                official ? "OFFICIAL" : "EVIDENCE");
    }

    private static Item toTaskItem(Submission submission, Set<UUID> assignmentIds) {
        Task task = submission.getTask();
        if (task == null || task.getTeacherAssignment() == null) {
            return null;
        }
        TeacherAssignment assignment = task.getTeacherAssignment();
        if (assignment.getId() == null || !assignmentIds.contains(assignment.getId())) {
            return null;
        }
        BigDecimal score = submission.getGrade() == null
                ? null : BigDecimal.valueOf(submission.getGrade().intValue());
        Instant recordedAt = submission.getGradedAt() != null
                ? submission.getGradedAt() : submission.getUpdatedAt();
        return new Item(
                submission.getPublicUuid(),
                assignment.getPublicUuid(),
                assignment.getCourse() != null ? assignment.getCourse().getPublicUuid() : null,
                assignment.getCourse() != null ? assignment.getCourse().getName() : null,
                task.getSection() != null ? task.getSection().getName() : null,
                task.getTitle(),
                "TASK",
                null,
                null,
                score,
                BigDecimal.valueOf(task.getMaxScore()),
                null,
                submission.getFeedback(),
                teacherDisplayName(assignment.getTeacher()),
                assignment.getAcademicPeriod() != null
                        ? assignment.getAcademicPeriod().getPublicUuid() : null,
                task.getDueAt(),
                recordedAt,
                false,
                "TASK",
                "CLASSWORK");
    }

    private static boolean countsTowardAverage(
            Evaluation evaluation,
            GradeRecord cell,
            boolean literalSheet,
            List<GradeBookCategoryEntry> categories) {
        if (!GradeAverageCalculator.isContributingStatus(evaluation.getStatus())) {
            return false;
        }
        if (!categoryContributes(evaluation, categories)) {
            return false;
        }
        if (literalSheet) {
            return evaluation.getScale() == EvaluationScale.LITERAL_CNEB
                    && CnebLiteralScale.points(cell.getLiteral()) != null;
        }
        return evaluation.getScale() == EvaluationScale.SCORE_0_20 && cell.getScore() != null;
    }

    private static boolean categoryContributes(
            Evaluation evaluation,
            List<GradeBookCategoryEntry> categories) {
        if (categories.isEmpty()) {
            return true;
        }
        if (evaluation.getCategory() == null) {
            return false;
        }
        UUID categoryId = evaluation.getCategory().getPublicUuid();
        return categories.stream()
                .anyMatch(category -> category.contributesToAverage()
                        && categoryId.equals(category.publicUuid()));
    }

    private static GradeBookCategoryEntry toCategoryEntry(GradeCategoryResponse category) {
        return new GradeBookCategoryEntry(
                category.publicUuid(),
                category.name(),
                category.colorToken(),
                category.sortOrder(),
                category.weight(),
                category.contributesToAverage(),
                category.allowsTeacherColumns()
                        || category.columnMode() == GradeCategoryColumnMode.OPEN);
    }

    private static MeGradesSummaryResponse.Period toPeriod(
            Optional<AcademicPeriod> selectedPeriod,
            Optional<AcademicPeriod> currentPeriod) {
        return selectedPeriod
                .map(period -> new MeGradesSummaryResponse.Period(
                        period.getPublicUuid(),
                        period.getName(),
                        currentPeriod.map(current -> current.getId().equals(period.getId())).orElse(false)))
                .orElse(null);
    }

    private static BigDecimal meanOf(List<Course> courses) {
        List<BigDecimal> numeric = courses.stream()
                .map(Course::average)
                .filter(Objects::nonNull)
                .toList();
        if (numeric.isEmpty()) {
            return null;
        }
        BigDecimal sum = numeric.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(numeric.size()), 2, RoundingMode.HALF_UP);
    }
}
