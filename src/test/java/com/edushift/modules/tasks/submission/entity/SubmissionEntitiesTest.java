package com.edushift.modules.tasks.submission.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Submission entities")
class SubmissionEntitiesTest {

    // =====================================================================
    // SubmissionStatus enum
    // =====================================================================

    @Nested
    @DisplayName("SubmissionStatus")
    class SubmissionStatusTests {

        @Test
        @DisplayName("values() — SUBMITTED, GRADED, SOFT_DELETED")
        void values() {
            assertThat(SubmissionStatus.values())
                    .containsExactly(
                            SubmissionStatus.SUBMITTED,
                            SubmissionStatus.GRADED,
                            SubmissionStatus.SOFT_DELETED);
        }

        @Test
        @DisplayName("valueOf round-trip")
        void valueOf() {
            assertThat(SubmissionStatus.valueOf("SUBMITTED")).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(SubmissionStatus.valueOf("GRADED")).isEqualTo(SubmissionStatus.GRADED);
            assertThat(SubmissionStatus.valueOf("SOFT_DELETED")).isEqualTo(SubmissionStatus.SOFT_DELETED);
        }
    }

    // =====================================================================
    // Submission
    // =====================================================================

    @Nested
    @DisplayName("Submission")
    class SubmissionTests {

        @Test
        @DisplayName("@PrePersist — publicUuid + status default to SUBMITTED")
        void prePersistDefaults() throws Exception {
            var s = new Submission();
            assertThat(s.getPublicUuid()).isNull();
            assertThat(s.getStatus()).isNull();

            invokePrePersist(s);

            assertThat(s.getPublicUuid()).isNotNull();
            assertThat(s.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        }

        @Test
        @DisplayName("@PrePersist — existing publicUuid + status are preserved")
        void prePersistPreserves() throws Exception {
            var s = new Submission();
            UUID pre = UUID.randomUUID();
            setField(s, "publicUuid", pre);
            s.setStatus(SubmissionStatus.GRADED);

            invokePrePersist(s);

            assertThat(s.getPublicUuid()).isEqualTo(pre);
            assertThat(s.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        }

        @Test
        @DisplayName("setters — all fields round-trip")
        void setters() {
            var s = new Submission();
            s.setStudentUserId(UUID.randomUUID());
            s.setSubmitterUserId(UUID.randomUUID());
            s.setTextBody("cuerpo");
            s.setAttachmentPublicUuid(UUID.randomUUID());
            s.setStatus(SubmissionStatus.SUBMITTED);
            s.setGrade((short) 85);
            s.setFeedback("bien");
            s.setGradedByUserId(UUID.randomUUID());
            s.setDeletedAt(java.time.Instant.now());

            assertThat(s.getTextBody()).isEqualTo("cuerpo");
            assertThat(s.getGrade()).isEqualTo((short) 85);
            assertThat(s.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(s.getFeedback()).isEqualTo("bien");
            assertThat(s.getGradedByUserId()).isNotNull();
            assertThat(s.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("tenant-aware base — can set tenantId")
        void tenantIdSetter() {
            var s = new Submission();
            UUID tenant = UUID.randomUUID();
            s.setTenantId(tenant);
            assertThat(s.getTenantId()).isEqualTo(tenant);
        }

        @Test
        @DisplayName("toString — publicUuid, status, grade")
        void toStringIncludes() {
            var s = new Submission();
            s.setStatus(SubmissionStatus.GRADED);
            s.setGrade((short) 90);
            String str = s.toString();
            assertThat(str).contains("publicUuid").contains("status").contains("grade");
        }
    }

    // =====================================================================
    // SubmissionRevision
    // =====================================================================

    @Nested
    @DisplayName("SubmissionRevision")
    class SubmissionRevisionTests {

        @Test
        @DisplayName("setters — all fields round-trip")
        void setters() {
            var r = new SubmissionRevision();
            r.setRevisionNumber((short) 3);
            r.setTextBody("prev body");
            r.setAttachmentPublicUuid(UUID.randomUUID());
            r.setCreatedByUserId(UUID.randomUUID());

            assertThat(r.getRevisionNumber()).isEqualTo((short) 3);
            assertThat(r.getTextBody()).isEqualTo("prev body");
            assertThat(r.getAttachmentPublicUuid()).isNotNull();
            assertThat(r.getCreatedByUserId()).isNotNull();
        }

        @Test
        @DisplayName("tenant-aware base — can set tenantId")
        void tenantIdSetter() {
            var r = new SubmissionRevision();
            UUID tenant = UUID.randomUUID();
            r.setTenantId(tenant);
            assertThat(r.getTenantId()).isEqualTo(tenant);
        }

        @Test
        @DisplayName("toString — includes revisionNumber")
        void toStringIncludes() {
            var r = new SubmissionRevision();
            r.setRevisionNumber((short) 5);
            assertThat(r.toString()).contains("revisionNumber");
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static void invokePrePersist(Submission s) throws Exception {
        var m = Submission.class.getDeclaredMethod("onPrePersist");
        m.setAccessible(true);
        m.invoke(s);
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