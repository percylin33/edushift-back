package com.edushift.modules.tasks.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.TaskSummary;
import com.edushift.modules.tasks.entity.Task;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TaskMapper")
class TaskMapperTest {

    private final TaskMapper mapper = new TaskMapper();

    private Task task;

    @BeforeEach
    void setUp() {
        task = new Task();
        setField(task, "publicUuid", UUID.randomUUID());
        Section section = new Section();
        setField(section, "publicUuid", UUID.randomUUID());
        task.setSection(section);
        task.setTitle("Algebra Tarea 1");
        task.setDescription("Resolver los ejercicios 1-10");
        task.setDueAt(Instant.parse("2026-06-10T00:00:00Z"));
        task.setAttachmentPublicUuid(UUID.randomUUID());
        task.setOwnerUserId(UUID.randomUUID());
        task.setAllowResubmission(true);
        task.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        task.setUpdatedAt(Instant.parse("2026-05-15T10:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields")
        void mapsAllFields() {
            TaskResponse r = mapper.toResponse(task);

            assertThat(r.publicUuid()).isEqualTo(task.getPublicUuid());
            assertThat(r.sectionPublicUuid()).isEqualTo(task.getSection().getPublicUuid());
            assertThat(r.title()).isEqualTo("Algebra Tarea 1");
            assertThat(r.description()).isEqualTo("Resolver los ejercicios 1-10");
            assertThat(r.dueAt()).isEqualTo(task.getDueAt());
            assertThat(r.attachmentPublicUuid()).isEqualTo(task.getAttachmentPublicUuid());
            assertThat(r.ownerPublicUuid()).isEqualTo(task.getOwnerUserId());
            assertThat(r.allowResubmission()).isTrue();
            assertThat(r.createdAt()).isEqualTo(task.getCreatedAt());
            assertThat(r.updatedAt()).isEqualTo(task.getUpdatedAt());
        }

        @Test
        @DisplayName("null section — sectionPublicUuid is null")
        void nullSection() {
            task.setSection(null);
            TaskResponse r = mapper.toResponse(task);
            assertThat(r.sectionPublicUuid()).isNull();
            assertThat(r.title()).isEqualTo("Algebra Tarea 1");
        }
    }

    @Nested
    @DisplayName("toSummary")
    class ToSummary {

        @Test
        @DisplayName("maps lean projection + hasAttachment true")
        void mapsAllFields() {
            TaskSummary s = mapper.toSummary(task);

            assertThat(s.publicUuid()).isEqualTo(task.getPublicUuid());
            assertThat(s.title()).isEqualTo("Algebra Tarea 1");
            assertThat(s.dueAt()).isEqualTo(task.getDueAt());
            assertThat(s.hasAttachment()).isTrue();
            assertThat(s.ownerPublicUuid()).isEqualTo(task.getOwnerUserId());
            assertThat(s.createdAt()).isEqualTo(task.getCreatedAt());
        }

        @Test
        @DisplayName("null attachment — hasAttachment is false")
        void nullAttachment() {
            task.setAttachmentPublicUuid(null);
            TaskSummary s = mapper.toSummary(task);
            assertThat(s.hasAttachment()).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

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