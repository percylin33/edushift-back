package com.edushift.modules.tasks.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.section.entity.Section;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Task entity")
class TaskEntityTest {

    @Test
    @DisplayName("default allowResubmission is true")
    void defaultAllowResubmission() {
        var t = new Task();
        assertThat(t.isAllowResubmission()).isTrue();
    }

    @Test
    @DisplayName("@PrePersist — publicUuid auto-generated when null")
    void prePersistGeneratesUuid() throws Exception {
        var t = new Task();
        assertThat(t.getPublicUuid()).isNull();
        invokePrePersist(t);
        assertThat(t.getPublicUuid()).isNotNull();
    }

    @Test
    @DisplayName("@PrePersist — existing publicUuid is preserved")
    void prePersistPreservesUuid() throws Exception {
        var t = new Task();
        UUID pre = UUID.randomUUID();
        setField(t, "publicUuid", pre);
        invokePrePersist(t);
        assertThat(t.getPublicUuid()).isEqualTo(pre);
    }

    @Test
    @DisplayName("setters — all fields round-trip")
    void setters() {
        var t = new Task();
        Section s = new Section();
        t.setSection(s);
        t.setTitle("Tarea 1");
        t.setDescription("Resolver");
        t.setDueAt(Instant.parse("2026-06-10T00:00:00Z"));
        t.setAttachmentPublicUuid(UUID.randomUUID());
        t.setOwnerUserId(UUID.randomUUID());
        t.setAllowResubmission(false);
        t.setDeletedAt(Instant.now());

        assertThat(t.getSection()).isSameAs(s);
        assertThat(t.getTitle()).isEqualTo("Tarea 1");
        assertThat(t.getDescription()).isEqualTo("Resolver");
        assertThat(t.getDueAt()).isEqualTo(Instant.parse("2026-06-10T00:00:00Z"));
        assertThat(t.getAttachmentPublicUuid()).isNotNull();
        assertThat(t.getOwnerUserId()).isNotNull();
        assertThat(t.isAllowResubmission()).isFalse();
        assertThat(t.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("tenant-aware base — can set tenantId")
    void tenantIdSetter() {
        var t = new Task();
        UUID tenant = UUID.randomUUID();
        t.setTenantId(tenant);
        assertThat(t.getTenantId()).isEqualTo(tenant);
    }

    @Test
    @DisplayName("toString includes publicUuid, title, dueAt")
    void toStringIncludes() {
        var t = new Task();
        t.setTitle("X");
        String s = t.toString();
        assertThat(s).contains("publicUuid").contains("title").contains("dueAt");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void invokePrePersist(Task t) throws Exception {
        var m = Task.class.getDeclaredMethod("onPrePersist");
        m.setAccessible(true);
        m.invoke(t);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            }
            catch (NoSuchFieldException ignore) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}