package com.edushift.modules.tasks.submission.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.exception.TaskNotFoundException;
import com.edushift.modules.tasks.repository.TaskRepository;
import com.edushift.modules.tasks.submission.dto.CreateSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.GradeSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.SubmissionResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionSummary;
import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionRevision;
import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import com.edushift.modules.tasks.submission.exception.AssignmentPastDueException;
import com.edushift.modules.tasks.submission.exception.GradeOutOfRangeException;
import com.edushift.modules.tasks.submission.exception.ResubmissionNotAllowedException;
import com.edushift.modules.tasks.submission.exception.SubmissionNotFoundException;
import com.edushift.modules.tasks.submission.mapper.SubmissionMapper;
import com.edushift.modules.tasks.submission.repository.SubmissionRepository;
import com.edushift.modules.tasks.submission.repository.SubmissionRevisionRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionServiceImpl")
class SubmissionServiceImplTest {

    @Mock private TaskRepository taskRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionRevisionRepository revisionRepository;
    @Mock private FileObjectService fileObjectService;
    @Mock private SubmissionMapper submissionMapper;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks private SubmissionServiceImpl service;

    private Task task;

    @BeforeEach
    void setUp() {
        task = new Task();
        setField(task, "publicUuid", UUID.randomUUID());
        setField(task, "id", UUID.randomUUID());
        task.setTitle("Tarea 1");
        task.setAllowResubmission(true);
        task.setDueAt(Instant.now().plus(2, ChronoUnit.DAYS));
    }

    // =====================================================================
    // submit
    // =====================================================================

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("first-time submit — creates row, wasIdempotent=false")
        void firstTime() {
            UUID student = UUID.randomUUID();
            UUID submitter = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, "mi respuesta", null);

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.empty());
            when(submissionRepository.save(any(Submission.class)))
                    .thenAnswer(inv -> {
                        Submission s = inv.getArgument(0);
                        setField(s, "id", UUID.randomUUID());
                        if (s.getPublicUuid() == null) {
                            setField(s, "publicUuid", UUID.randomUUID());
                        }
                        return s;
                    });
            when(submissionMapper.toResponse(any(Submission.class), any()))
                    .thenAnswer(inv -> stubResponse((Submission) inv.getArgument(0), (Boolean) inv.getArgument(1)));

            SubmissionResponse resp = service.submit(task.getPublicUuid(), req, submitter);

            assertThat(resp.studentPublicUuid()).isEqualTo(student);
            assertThat(resp.submitterPublicUuid()).isEqualTo(submitter);
            assertThat(resp.wasIdempotent()).isFalse();

            ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
            verify(submissionRepository).save(captor.capture());
            Submission saved = captor.getValue();
            assertThat(saved.getTask()).isSameAs(task);
            assertThat(saved.getStudentUserId()).isEqualTo(student);
            assertThat(saved.getSubmitterUserId()).isEqualTo(submitter);
            assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(saved.getGrade()).isNull();
            assertThat(saved.getGradedByUserId()).isNull();

            verify(revisionRepository, never()).save(any());
        }

        @Test
        @DisplayName("first-time submit with attachment — validates file + acquires reference")
        void firstTimeWithAttachment() {
            UUID student = UUID.randomUUID();
            UUID submitter = UUID.randomUUID();
            UUID attach = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, null, attach);

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.empty());
            when(fileObjectService.findByPublicUuid(attach))
                    .thenReturn(Optional.of(new FileObject()));
            when(submissionRepository.save(any(Submission.class)))
                    .thenAnswer(inv -> {
                        Submission s = inv.getArgument(0);
                        setField(s, "id", UUID.randomUUID());
                        if (s.getPublicUuid() == null) {
                            setField(s, "publicUuid", UUID.randomUUID());
                        }
                        return s;
                    });
            when(submissionMapper.toResponse(any(Submission.class), any()))
                    .thenAnswer(inv -> stubResponse((Submission) inv.getArgument(0), (Boolean) inv.getArgument(1)));

            service.submit(task.getPublicUuid(), req, submitter);

            verify(fileObjectService).findByPublicUuid(attach);
            verify(fileObjectService).acquireReference(attach);
        }

        @Test
        @DisplayName("attachment not in tenant — BadRequestException FILE_NOT_FOUND")
        void attachmentMissing() {
            UUID student = UUID.randomUUID();
            UUID attach = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, "body", attach);

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.empty());
            when(submissionRepository.save(any(Submission.class)))
                    .thenAnswer(inv -> {
                        Submission s = inv.getArgument(0);
                        setField(s, "id", UUID.randomUUID());
                        if (s.getPublicUuid() == null) {
                            setField(s, "publicUuid", UUID.randomUUID());
                        }
                        return s;
                    });
            when(fileObjectService.findByPublicUuid(attach))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(task.getPublicUuid(), req, UUID.randomUUID()))
                    .isInstanceOf(com.edushift.shared.exception.BadRequestException.class)
                    .satisfies(t -> assertThat(
                            ((com.edushift.shared.exception.ApiException) t).getCode())
                            .isEqualTo("FILE_NOT_FOUND"));
        }

        @Test
        @DisplayName("empty payload (no text + no attachment) — BadRequestException INCONSISTENT_PAYLOAD")
        void emptyPayload() {
            UUID student = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, null, null);

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));

            assertThatThrownBy(() -> service.submit(task.getPublicUuid(), req, UUID.randomUUID()))
                    .isInstanceOf(com.edushift.shared.exception.BadRequestException.class)
                    .satisfies(t -> assertThat(
                            ((com.edushift.shared.exception.ApiException) t).getCode())
                            .isEqualTo("INCONSISTENT_PAYLOAD"));

            verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("task past due — AssignmentPastDueException")
        void pastDue() {
            task.setDueAt(Instant.now().minus(1, ChronoUnit.HOURS));
            UUID student = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, "body", null);

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));

            assertThatThrownBy(() -> service.submit(task.getPublicUuid(), req, UUID.randomUUID()))
                    .isInstanceOf(AssignmentPastDueException.class);

            verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("task without dueAt — allowed")
        void noDueAt() {
            task.setDueAt(null);
            UUID student = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, "body", null);

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.empty());
            when(submissionRepository.save(any(Submission.class)))
                    .thenAnswer(inv -> {
                        Submission s = inv.getArgument(0);
                        setField(s, "id", UUID.randomUUID());
                        if (s.getPublicUuid() == null) {
                            setField(s, "publicUuid", UUID.randomUUID());
                        }
                        return s;
                    });
            when(submissionMapper.toResponse(any(Submission.class), any()))
                    .thenAnswer(inv -> stubResponse((Submission) inv.getArgument(0), (Boolean) inv.getArgument(1)));

            SubmissionResponse resp = service.submit(task.getPublicUuid(), req, UUID.randomUUID());
            assertThat(resp.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        }

        @Test
        @DisplayName("task not found — TaskNotFoundException")
        void taskMissing() {
            UUID missing = UUID.randomUUID();
            var req = new CreateSubmissionRequest(UUID.randomUUID(), "body", null);

            when(taskRepository.findByPublicUuid(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(missing, req, UUID.randomUUID()))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("re-submit allowed — snapshots revision, releases old attachment, wasIdempotent=true")
        void resubmitAllowed() {
            UUID student = UUID.randomUUID();
            UUID submitter = UUID.randomUUID();
            UUID oldAttach = UUID.randomUUID();
            UUID newAttach = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, "nueva respuesta", newAttach);

            Submission existing = new Submission();
            existing.setTask(task);
            existing.setStudentUserId(student);
            existing.setSubmitterUserId(submitter);
            existing.setAttachmentPublicUuid(oldAttach);
            existing.setStatus(SubmissionStatus.SUBMITTED);
            existing.setTextBody("vieja respuesta");
            setField(existing, "publicUuid", UUID.randomUUID());
            setField(existing, "id", UUID.randomUUID());

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.of(existing));
            when(revisionRepository.findMaxRevisionNumber(existing))
                    .thenReturn((short) 2);
            when(revisionRepository.save(any(SubmissionRevision.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(fileObjectService.findByPublicUuid(newAttach))
                    .thenReturn(Optional.of(new FileObject()));
            when(submissionRepository.save(any(Submission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(submissionMapper.toResponse(any(Submission.class), any()))
                    .thenAnswer(inv -> stubResponse((Submission) inv.getArgument(0), (Boolean) inv.getArgument(1)));

            service.submit(task.getPublicUuid(), req, submitter);

            ArgumentCaptor<SubmissionRevision> revCaptor = ArgumentCaptor.forClass(SubmissionRevision.class);
            verify(revisionRepository).save(revCaptor.capture());
            SubmissionRevision rev = revCaptor.getValue();
            assertThat(rev.getRevisionNumber()).isEqualTo((short) 3);
            assertThat(rev.getTextBody()).isEqualTo("vieja respuesta");
            assertThat(rev.getAttachmentPublicUuid()).isEqualTo(oldAttach);
            assertThat(rev.getCreatedByUserId()).isEqualTo(submitter);

            verify(fileObjectService).releaseReference(oldAttach);
            verify(fileObjectService).acquireReference(newAttach);
        }

        @Test
        @DisplayName("re-submit with allowResubmission=false — ResubmissionNotAllowedException")
        void resubmitBlocked() {
            task.setAllowResubmission(false);
            UUID student = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, "body", null);

            Submission existing = new Submission();
            existing.setTask(task);
            existing.setStudentUserId(student);
            setField(existing, "publicUuid", UUID.randomUUID());

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.submit(task.getPublicUuid(), req, UUID.randomUUID()))
                    .isInstanceOf(ResubmissionNotAllowedException.class);

            verify(revisionRepository, never()).save(any());
            verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("first revision number starts at 1 when no prior revisions")
        void firstRevision() {
            UUID student = UUID.randomUUID();
            UUID submitter = UUID.randomUUID();
            UUID oldAttach = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, "nueva", null);

            Submission existing = new Submission();
            existing.setTask(task);
            existing.setStudentUserId(student);
            existing.setSubmitterUserId(submitter);
            existing.setAttachmentPublicUuid(oldAttach);
            existing.setStatus(SubmissionStatus.SUBMITTED);
            existing.setTextBody("vieja");
            setField(existing, "publicUuid", UUID.randomUUID());

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.of(existing));
            when(revisionRepository.findMaxRevisionNumber(existing))
                    .thenReturn(null);
            when(revisionRepository.save(any(SubmissionRevision.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(submissionRepository.save(any(Submission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(submissionMapper.toResponse(any(Submission.class), any()))
                    .thenAnswer(inv -> stubResponse((Submission) inv.getArgument(0), (Boolean) inv.getArgument(1)));

            service.submit(task.getPublicUuid(), req, submitter);

            ArgumentCaptor<SubmissionRevision> revCaptor = ArgumentCaptor.forClass(SubmissionRevision.class);
            verify(revisionRepository).save(revCaptor.capture());
            assertThat(revCaptor.getValue().getRevisionNumber()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("re-submit clears previous grade/feedback (teacher must re-grade)")
        void resubmitClearsGrade() {
            UUID student = UUID.randomUUID();
            var req = new CreateSubmissionRequest(student, "nueva", null);

            Submission existing = new Submission();
            existing.setTask(task);
            existing.setStudentUserId(student);
            existing.setGrade((short) 80);
            existing.setFeedback("bueno");
            existing.setGradedByUserId(UUID.randomUUID());
            existing.setGradedAt(Instant.now());
            existing.setStatus(SubmissionStatus.GRADED);
            setField(existing, "publicUuid", UUID.randomUUID());

            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.of(existing));
            when(revisionRepository.findMaxRevisionNumber(existing)).thenReturn((short) 1);
            when(revisionRepository.save(any(SubmissionRevision.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(submissionRepository.save(any(Submission.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(submissionMapper.toResponse(any(Submission.class), any()))
                    .thenAnswer(inv -> stubResponse((Submission) inv.getArgument(0), (Boolean) inv.getArgument(1)));

            service.submit(task.getPublicUuid(), req, UUID.randomUUID());

            assertThat(existing.getGrade()).isNull();
            assertThat(existing.getFeedback()).isNull();
            assertThat(existing.getGradedByUserId()).isNull();
            assertThat(existing.getGradedAt()).isNull();
            assertThat(existing.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        }
    }

    // =====================================================================
    // listByTask
    // =====================================================================

    @Nested
    @DisplayName("listByTask")
    class ListByTask {

        @Test
        @DisplayName("returns mapped page")
        void happy() {
            Pageable p = PageRequest.of(0, 20);
            Submission s = new Submission();
            setField(s, "publicUuid", UUID.randomUUID());
            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findAllByTaskOrderByCreatedAtDesc(task, p))
                    .thenReturn(new PageImpl<>(List.of(s), p, 1));
            SubmissionSummary sum = new SubmissionSummary(
                    s.getPublicUuid(), UUID.randomUUID(),
                    SubmissionStatus.SUBMITTED, null, false, Instant.now());
            when(submissionMapper.toSummary(s)).thenReturn(sum);

            Page<SubmissionSummary> page = service.listByTask(task.getPublicUuid(), p);

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).containsExactly(sum);
        }

        @Test
        @DisplayName("task not found — TaskNotFoundException")
        void missingTask() {
            UUID id = UUID.randomUUID();
            when(taskRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listByTask(id, PageRequest.of(0, 20)))
                    .isInstanceOf(TaskNotFoundException.class);
        }
    }

    // =====================================================================
    // getMine
    // =====================================================================

    @Nested
    @DisplayName("getMine")
    class GetMine {

        @Test
        @DisplayName("existing submission — returns response")
        void found() {
            UUID student = UUID.randomUUID();
            Submission s = new Submission();
            setField(s, "publicUuid", UUID.randomUUID());
            s.setStudentUserId(student);
            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.of(s));
            SubmissionResponse r = new SubmissionResponse(
                    s.getPublicUuid(), task.getPublicUuid(), student, student,
                    "body", null, SubmissionStatus.SUBMITTED,
                    null, null, null, null, null,
                    Instant.now(), Instant.now());
            when(submissionMapper.toResponse(s)).thenReturn(r);

            SubmissionResponse result = service.getMine(task.getPublicUuid(), student);
            assertThat(result).isSameAs(r);
        }

        @Test
        @DisplayName("no submission yet — returns null")
        void empty() {
            UUID student = UUID.randomUUID();
            when(taskRepository.findByPublicUuid(task.getPublicUuid()))
                    .thenReturn(Optional.of(task));
            when(submissionRepository.findByTaskAndStudentUserId(task, student))
                    .thenReturn(Optional.empty());

            assertThat(service.getMine(task.getPublicUuid(), student)).isNull();
        }

        @Test
        @DisplayName("task not found — TaskNotFoundException")
        void taskMissing() {
            UUID id = UUID.randomUUID();
            when(taskRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMine(id, UUID.randomUUID()))
                    .isInstanceOf(TaskNotFoundException.class);
        }
    }

    // =====================================================================
    // grade
    // =====================================================================

    @Nested
    @DisplayName("grade")
    class Grade {

        @Test
        @DisplayName("happy path — sets grade, feedback, status=GRADED")
        void happy() {
            Submission s = newSubmission();
            UUID grader = UUID.randomUUID();
            var req = new GradeSubmissionRequest(90, "excelente");

            when(submissionRepository.findByPublicUuid(s.getPublicUuid()))
                    .thenReturn(Optional.of(s));
            when(submissionRepository.save(s)).thenReturn(s);
            when(submissionMapper.toResponse(s)).thenAnswer(inv -> stubResponse((Submission) inv.getArgument(0), null));

            SubmissionResponse resp = service.grade(s.getPublicUuid(), req, grader);

            assertThat(s.getGrade()).isEqualTo((short) 90);
            assertThat(s.getFeedback()).isEqualTo("excelente");
            assertThat(s.getGradedByUserId()).isEqualTo(grader);
            assertThat(s.getGradedAt()).isNotNull();
            assertThat(s.getStatus()).isEqualTo(SubmissionStatus.GRADED);
            assertThat(resp.grade()).isEqualTo(90);
        }

        @Test
        @DisplayName("grade < 0 — GradeOutOfRangeException")
        void negative() {
            assertThatThrownBy(() -> service.grade(UUID.randomUUID(),
                    new GradeSubmissionRequest(-1, "x"), UUID.randomUUID()))
                    .isInstanceOf(GradeOutOfRangeException.class)
                    .hasMessageContaining("-1");

            verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("grade > 100 — GradeOutOfRangeException")
        void aboveHundred() {
            assertThatThrownBy(() -> service.grade(UUID.randomUUID(),
                    new GradeSubmissionRequest(101, "x"), UUID.randomUUID()))
                    .isInstanceOf(GradeOutOfRangeException.class);

            verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("grade=0 — boundary, allowed")
        void zero() {
            Submission s = newSubmission();
            when(submissionRepository.findByPublicUuid(s.getPublicUuid()))
                    .thenReturn(Optional.of(s));
            when(submissionRepository.save(s)).thenReturn(s);
            when(submissionMapper.toResponse(s)).thenReturn(stubResponse(s, null));

            service.grade(s.getPublicUuid(),
                    new GradeSubmissionRequest(0, "needs work"), UUID.randomUUID());

            assertThat(s.getGrade()).isEqualTo((short) 0);
        }

        @Test
        @DisplayName("grade=100 — boundary, allowed")
        void hundred() {
            Submission s = newSubmission();
            when(submissionRepository.findByPublicUuid(s.getPublicUuid()))
                    .thenReturn(Optional.of(s));
            when(submissionRepository.save(s)).thenReturn(s);
            when(submissionMapper.toResponse(s)).thenReturn(stubResponse(s, null));

            service.grade(s.getPublicUuid(),
                    new GradeSubmissionRequest(100, "perfecto"), UUID.randomUUID());

            assertThat(s.getGrade()).isEqualTo((short) 100);
        }

        @Test
        @DisplayName("submission missing — SubmissionNotFoundException")
        void missing() {
            UUID id = UUID.randomUUID();
            when(submissionRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.grade(id,
                    new GradeSubmissionRequest(50, "fb"), UUID.randomUUID()))
                    .isInstanceOf(SubmissionNotFoundException.class);

            verify(submissionRepository, never()).save(any());
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private Submission newSubmission() {
        Submission s = new Submission();
        setField(s, "publicUuid", UUID.randomUUID());
        setField(s, "id", UUID.randomUUID());
        s.setTask(task);
        s.setStudentUserId(UUID.randomUUID());
        s.setSubmitterUserId(UUID.randomUUID());
        s.setStatus(SubmissionStatus.SUBMITTED);
        s.setTextBody("body");
        return s;
    }

    private SubmissionResponse stubResponse(Submission s, Boolean wasIdempotent) {
        return new SubmissionResponse(
                s.getPublicUuid(),
                s.getTask() != null ? s.getTask().getPublicUuid() : null,
                s.getStudentUserId(),
                s.getSubmitterUserId(),
                s.getTextBody(),
                s.getAttachmentPublicUuid(),
                s.getStatus(),
                s.getGrade() != null ? s.getGrade().intValue() : null,
                s.getFeedback(),
                s.getGradedByUserId(),
                s.getGradedAt(),
                wasIdempotent,
                s.getCreatedAt(),
                s.getUpdatedAt());
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