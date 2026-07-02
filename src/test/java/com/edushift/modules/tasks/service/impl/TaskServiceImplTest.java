package com.edushift.modules.tasks.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.tasks.dto.CreateTaskRequest;
import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.UpdateTaskRequest;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.exception.DueAtInPastException;
import com.edushift.modules.tasks.exception.RecordEmptyPatchException;
import com.edushift.modules.tasks.exception.SectionNotFoundException;
import com.edushift.modules.tasks.exception.TaskNotFoundException;
import com.edushift.modules.tasks.mapper.TaskMapper;
import com.edushift.modules.tasks.repository.TaskRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
@DisplayName("TaskServiceImpl")
class TaskServiceImplTest {

    @Mock private TaskRepository taskRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private FileObjectService fileObjectService;
    @Mock private TaskMapper taskMapper;

    @InjectMocks private TaskServiceImpl service;

    private Section section;

    @BeforeEach
    void setUp() {
        section = new Section();
        setField(section, "publicUuid", UUID.randomUUID());
        setField(section, "id", UUID.randomUUID());
    }

    // =====================================================================
    // create
    // =====================================================================

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("happy path — section exists, dueAt in future, no attachment")
        void happyNoAttachment() {
            var req = new CreateTaskRequest(
                    "Algebra Tarea 1",
                    "Resolver",
                    Instant.now().plus(2, ChronoUnit.DAYS),
                    null);
            UUID owner = UUID.randomUUID();

            when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
                    .thenReturn(Optional.of(section));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskMapper.toResponse(any(Task.class)))
                    .thenAnswer(inv -> toResponse((Task) inv.getArgument(0)));

            TaskResponse resp = service.create(section.getPublicUuid(), req, owner);

            assertThat(resp.title()).isEqualTo("Algebra Tarea 1");
            assertThat(resp.allowResubmission()).isTrue();
            assertThat(resp.attachmentPublicUuid()).isNull();

            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(captor.capture());
            Task saved = captor.getValue();
            assertThat(saved.getSection()).isSameAs(section);
            assertThat(saved.getOwnerUserId()).isEqualTo(owner);
            assertThat(saved.isAllowResubmission()).isTrue();
            verify(fileObjectService, never()).acquireReference(any());
        }

        @Test
        @DisplayName("with attachment — file lookup + acquireReference called once")
        void withAttachment() {
            UUID attach = UUID.randomUUID();
            var req = new CreateTaskRequest(
                    "Tarea con PDF", null,
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    attach);

            when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
                    .thenReturn(Optional.of(section));
            when(fileObjectService.findByPublicUuid(attach))
                    .thenReturn(Optional.of(new FileObject()));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskMapper.toResponse(any(Task.class)))
                    .thenAnswer(inv -> toResponse((Task) inv.getArgument(0)));

            service.create(section.getPublicUuid(), req, UUID.randomUUID());

            verify(fileObjectService, times(1)).findByPublicUuid(attach);
            verify(fileObjectService, times(1)).acquireReference(attach);
        }

        @Test
        @DisplayName("attachment not found in tenant — BadRequestException FILE_NOT_FOUND")
        void attachmentMissing() {
            UUID attach = UUID.randomUUID();
            var req = new CreateTaskRequest(
                    "T", null,
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    attach);

            when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
                    .thenReturn(Optional.of(section));
            when(fileObjectService.findByPublicUuid(attach))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.create(section.getPublicUuid(), req, UUID.randomUUID()))
                    .isInstanceOf(com.edushift.shared.exception.BadRequestException.class)
                    .satisfies(t -> assertThat(
                            ((com.edushift.shared.exception.ApiException) t).getCode())
                            .isEqualTo("FILE_NOT_FOUND"));

            verify(taskRepository, never()).save(any());
        }

        @Test
        @DisplayName("section not found — SectionNotFoundException")
        void sectionMissing() {
            UUID missingSection = UUID.randomUUID();
            var req = new CreateTaskRequest(
                    "T", null, Instant.now().plus(1, ChronoUnit.DAYS), null);

            when(sectionRepository.findByPublicUuid(missingSection))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.create(missingSection, req, UUID.randomUUID()))
                    .isInstanceOf(SectionNotFoundException.class);

            verify(taskRepository, never()).save(any());
        }

        @Test
        @DisplayName("dueAt in the past — DueAtInPastException, no save")
        void dueAtInPast() {
            var req = new CreateTaskRequest(
                    "T", null,
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    null);

            when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
                    .thenReturn(Optional.of(section));

            assertThatThrownBy(() ->
                    service.create(section.getPublicUuid(), req, UUID.randomUUID()))
                    .isInstanceOf(DueAtInPastException.class);

            verify(taskRepository, never()).save(any());
        }

        @Test
        @DisplayName("dueAt null — allowed (optional)")
        void dueAtNull() {
            var req = new CreateTaskRequest("T", "d", null, null);
            when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
                    .thenReturn(Optional.of(section));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskMapper.toResponse(any(Task.class)))
                    .thenAnswer(inv -> toResponse((Task) inv.getArgument(0)));

            TaskResponse resp = service.create(section.getPublicUuid(), req, UUID.randomUUID());
            assertThat(resp.title()).isEqualTo("T");
            assertThat(resp.dueAt()).isNull();
        }
    }

    // =====================================================================
    // listBySection
    // =====================================================================

    @Nested
    @DisplayName("listBySection")
    class ListBySection {

        @Test
        @DisplayName("section visible — returns mapped page")
        void visible() {
            Task t1 = newTask("T1");
            Task t2 = newTask("T2");
            Pageable p = PageRequest.of(0, 20);
            when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
                    .thenReturn(Optional.of(section));
            when(taskRepository.findAllBySectionOrderByDueAtDesc(section, p))
                    .thenReturn(new PageImpl<>(java.util.List.of(t1, t2), p, 2));
            when(taskMapper.toSummary(t1)).thenReturn(
                    new com.edushift.modules.tasks.dto.TaskSummary(
                            t1.getPublicUuid(), "T1", t1.getDueAt(), false,
                            t1.getOwnerUserId(), t1.getCreatedAt()));
            when(taskMapper.toSummary(t2)).thenReturn(
                    new com.edushift.modules.tasks.dto.TaskSummary(
                            t2.getPublicUuid(), "T2", t2.getDueAt(), false,
                            t2.getOwnerUserId(), t2.getCreatedAt()));

            var page = service.listBySection(section.getPublicUuid(), p);

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("section not visible to tenant — returns empty page (anti-enumeration)")
        void missingSection() {
            UUID stranger = UUID.randomUUID();
            Pageable p = PageRequest.of(0, 20);
            when(sectionRepository.findByPublicUuid(stranger))
                    .thenReturn(Optional.empty());

            Page<?> page = service.listBySection(stranger, p);
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }
    }

    // =====================================================================
    // getByPublicUuid
    // =====================================================================

    @Nested
    @DisplayName("getByPublicUuid")
    class Get {

        @Test
        @DisplayName("happy path")
        void happy() {
            Task t = newTask("Tarea 1");
            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));
            when(taskMapper.toResponse(t)).thenReturn(toResponse(t));

            TaskResponse r = service.getByPublicUuid(t.getPublicUuid());
            assertThat(r.title()).isEqualTo("Tarea 1");
        }

        @Test
        @DisplayName("missing — TaskNotFoundException")
        void missing() {
            UUID id = UUID.randomUUID();
            when(taskRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByPublicUuid(id))
                    .isInstanceOf(TaskNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    // =====================================================================
    // patch
    // =====================================================================

    @Nested
    @DisplayName("patch")
    class Patch {

        @Test
        @DisplayName("happy path — applies provided fields")
        void happy() {
            Task t = newTask("Original");
            var req = new UpdateTaskRequest(
                    "Nuevo", "Nueva desc",
                    Instant.now().plus(2, ChronoUnit.DAYS),
                    null, Boolean.FALSE);
            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));
            when(taskRepository.save(t)).thenReturn(t);
            when(taskMapper.toResponse(t)).thenAnswer(inv -> toResponse((Task) inv.getArgument(0)));

            TaskResponse r = service.patch(t.getPublicUuid(), req);

            assertThat(t.getTitle()).isEqualTo("Nuevo");
            assertThat(t.getDescription()).isEqualTo("Nueva desc");
            assertThat(t.isAllowResubmission()).isFalse();
            assertThat(r.title()).isEqualTo("Nuevo");
        }

        @Test
        @DisplayName("empty patch — RecordEmptyPatchException, no save")
        void emptyPatch() {
            Task t = newTask("Original");
            var req = new UpdateTaskRequest(null, null, null, null, null);

            assertThatThrownBy(() -> service.patch(t.getPublicUuid(), req))
                    .isInstanceOf(RecordEmptyPatchException.class);

            verify(taskRepository, never()).findByPublicUuid(any());
            verify(taskRepository, never()).save(any());
        }

        @Test
        @DisplayName("missing task — TaskNotFoundException")
        void missing() {
            UUID id = UUID.randomUUID();
            var req = new UpdateTaskRequest("X", null, null, null, null);
            when(taskRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.patch(id, req))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        @DisplayName("dueAt in the past on patch — DueAtInPastException")
        void dueAtInPast() {
            Task t = newTask("Original");
            var req = new UpdateTaskRequest(null, null,
                    Instant.now().minus(1, ChronoUnit.HOURS), null, null);
            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));

            assertThatThrownBy(() -> service.patch(t.getPublicUuid(), req))
                    .isInstanceOf(DueAtInPastException.class);

            verify(taskRepository, never()).save(any());
        }

        @Test
        @DisplayName("replace attachment — release old + acquire new")
        void replaceAttachment() {
            Task t = newTask("Original");
            UUID oldAttach = UUID.randomUUID();
            UUID newAttach = UUID.randomUUID();
            t.setAttachmentPublicUuid(oldAttach);

            var req = new UpdateTaskRequest(null, null, null, newAttach, null);

            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));
            when(fileObjectService.findByPublicUuid(newAttach))
                    .thenReturn(Optional.of(new FileObject()));
            when(taskRepository.save(t)).thenReturn(t);
            when(taskMapper.toResponse(t)).thenReturn(toResponse(t));

            service.patch(t.getPublicUuid(), req);

            verify(fileObjectService).releaseReference(oldAttach);
            verify(fileObjectService).acquireReference(newAttach);
            assertThat(t.getAttachmentPublicUuid()).isEqualTo(newAttach);
        }

        @Test
        @DisplayName("set attachment when none existed — acquire only")
        void setAttachmentNew() {
            Task t = newTask("Original");
            UUID newAttach = UUID.randomUUID();
            t.setAttachmentPublicUuid(null);

            var req = new UpdateTaskRequest(null, null, null, newAttach, null);

            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));
            when(fileObjectService.findByPublicUuid(newAttach))
                    .thenReturn(Optional.of(new FileObject()));
            when(taskRepository.save(t)).thenReturn(t);
            when(taskMapper.toResponse(t)).thenReturn(toResponse(t));

            service.patch(t.getPublicUuid(), req);

            verify(fileObjectService, never()).releaseReference(any());
            verify(fileObjectService).acquireReference(newAttach);
        }

        @Test
        @DisplayName("same attachment as current — no acquire/release")
        void sameAttachment() {
            Task t = newTask("Original");
            UUID attach = UUID.randomUUID();
            t.setAttachmentPublicUuid(attach);

            var req = new UpdateTaskRequest(null, null, null, attach, null);

            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));
            when(taskRepository.save(t)).thenReturn(t);
            when(taskMapper.toResponse(t)).thenReturn(toResponse(t));

            service.patch(t.getPublicUuid(), req);

            verify(fileObjectService, never()).releaseReference(any());
            verify(fileObjectService, never()).acquireReference(any());
        }

        @Test
        @DisplayName("new attachment not in tenant — BadRequestException FILE_NOT_FOUND")
        void attachmentMissingOnPatch() {
            Task t = newTask("Original");
            UUID newAttach = UUID.randomUUID();

            var req = new UpdateTaskRequest(null, null, null, newAttach, null);

            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));
            when(fileObjectService.findByPublicUuid(newAttach))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.patch(t.getPublicUuid(), req))
                    .isInstanceOf(com.edushift.shared.exception.BadRequestException.class)
                    .satisfies(th -> assertThat(
                            ((com.edushift.shared.exception.ApiException) th).getCode())
                            .isEqualTo("FILE_NOT_FOUND"));

            verify(taskRepository, never()).save(any());
        }
    }

    // =====================================================================
    // delete
    // =====================================================================

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("happy path — deletes + releases attachment")
        void happyWithAttachment() {
            Task t = newTask("X");
            UUID attach = UUID.randomUUID();
            t.setAttachmentPublicUuid(attach);
            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));

            service.delete(t.getPublicUuid());

            verify(taskRepository).delete(t);
            verify(fileObjectService).releaseReference(attach);
        }

        @Test
        @DisplayName("no attachment — delete only")
        void noAttachment() {
            Task t = newTask("X");
            t.setAttachmentPublicUuid(null);
            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));

            service.delete(t.getPublicUuid());

            verify(taskRepository).delete(t);
            verify(fileObjectService, never()).releaseReference(any());
        }

        @Test
        @DisplayName("releaseReference throws — warn logged, delete still succeeds")
        void releaseFailsIsSwallowed() {
            Task t = newTask("X");
            UUID attach = UUID.randomUUID();
            t.setAttachmentPublicUuid(attach);
            when(taskRepository.findByPublicUuid(t.getPublicUuid()))
                    .thenReturn(Optional.of(t));
            org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                    .when(fileObjectService).releaseReference(attach);

            service.delete(t.getPublicUuid());

            verify(taskRepository).delete(t);
        }

        @Test
        @DisplayName("missing — TaskNotFoundException")
        void missing() {
            UUID id = UUID.randomUUID();
            when(taskRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(id))
                    .isInstanceOf(TaskNotFoundException.class);

            verify(taskRepository, never()).delete(any());
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private Task newTask(String title) {
        Task t = new Task();
        setField(t, "publicUuid", UUID.randomUUID());
        setField(t, "id", UUID.randomUUID());
        t.setSection(section);
        t.setTitle(title);
        t.setDescription("desc");
        t.setDueAt(Instant.now().plus(1, ChronoUnit.DAYS));
        t.setOwnerUserId(UUID.randomUUID());
        t.setAllowResubmission(true);
        t.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        t.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return t;
    }

    private static TaskResponse toResponse(Task t) {
        return new TaskResponse(
                t.getPublicUuid(),
                t.getSection() != null ? t.getSection().getPublicUuid() : null,
                t.getTitle(),
                t.getDescription(),
                t.getDueAt(),
                t.getAttachmentPublicUuid(),
                t.getOwnerUserId(),
                t.isAllowResubmission(),
                t.getCreatedAt(),
                t.getUpdatedAt());
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