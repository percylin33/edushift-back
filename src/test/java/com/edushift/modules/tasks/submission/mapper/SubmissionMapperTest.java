package com.edushift.modules.tasks.submission.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.submission.dto.SubmissionResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionSummary;
import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SubmissionMapper")
class SubmissionMapperTest {

    private final SubmissionMapper mapper = new SubmissionMapper();

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("with status SUBMITTED, null grade")
        void submittedNoGrade() {
            var s = newSubmission(SubmissionStatus.SUBMITTED, null, "body");
            SubmissionResponse r = mapper.toResponse(s);
            assertThat(r.publicUuid()).isEqualTo(s.getPublicUuid());
            assertThat(r.taskPublicUuid()).isEqualTo(s.getTask().getPublicUuid());
            assertThat(r.studentPublicUuid()).isEqualTo(s.getStudentUserId());
            assertThat(r.submitterPublicUuid()).isEqualTo(s.getSubmitterUserId());
            assertThat(r.textBody()).isEqualTo("body");
            assertThat(r.status()).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(r.grade()).isNull();
            assertThat(r.feedback()).isNull();
            assertThat(r.gradedByPublicUuid()).isNull();
            assertThat(r.wasIdempotent()).isNull();
        }

        @Test
        @DisplayName("with status GRADED, grade + feedback + wasIdempotent=true")
        void graded() {
            var s = newSubmission(SubmissionStatus.GRADED, (short) 90, "body");
            s.setFeedback("excelente");
            s.setGradedByUserId(UUID.randomUUID());
            s.setGradedAt(Instant.parse("2026-06-15T10:00:00Z"));

            SubmissionResponse r = mapper.toResponse(s, Boolean.TRUE);

            assertThat(r.status()).isEqualTo(SubmissionStatus.GRADED);
            assertThat(r.grade()).isEqualTo(90);
            assertThat(r.feedback()).isEqualTo("excelente");
            assertThat(r.gradedByPublicUuid()).isEqualTo(s.getGradedByUserId());
            assertThat(r.gradedAt()).isEqualTo(Instant.parse("2026-06-15T10:00:00Z"));
            assertThat(r.wasIdempotent()).isTrue();
        }

        @Test
        @DisplayName("wasIdempotent=null when not provided")
        void wasIdempotentNull() {
            var s = newSubmission(SubmissionStatus.SUBMITTED, null, null);
            assertThat(mapper.toResponse(s).wasIdempotent()).isNull();
        }

        @Test
        @DisplayName("null task — taskPublicUuid is null")
        void nullTask() {
            var s = newSubmission(SubmissionStatus.SUBMITTED, null, null);
            s.setTask(null);
            assertThat(mapper.toResponse(s).taskPublicUuid()).isNull();
        }
    }

    @Nested
    @DisplayName("toSummary")
    class ToSummary {

        @Test
        @DisplayName("maps lean projection")
        void mapsAll() {
            var s = newSubmission(SubmissionStatus.SUBMITTED, null, "body");
            s.setAttachmentPublicUuid(UUID.randomUUID());

            SubmissionSummary sum = mapper.toSummary(s);

            assertThat(sum.publicUuid()).isEqualTo(s.getPublicUuid());
            assertThat(sum.studentPublicUuid()).isEqualTo(s.getStudentUserId());
            assertThat(sum.status()).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(sum.grade()).isNull();
            assertThat(sum.hasAttachment()).isTrue();
            assertThat(sum.createdAt()).isEqualTo(s.getCreatedAt());
        }

        @Test
        @DisplayName("graded + hasAttachment + grade is 100")
        void gradedFull() {
            var s = newSubmission(SubmissionStatus.GRADED, (short) 100, "ok");
            s.setAttachmentPublicUuid(UUID.randomUUID());

            SubmissionSummary sum = mapper.toSummary(s);

            assertThat(sum.status()).isEqualTo(SubmissionStatus.GRADED);
            assertThat(sum.grade()).isEqualTo(100);
            assertThat(sum.hasAttachment()).isTrue();
        }

        @Test
        @DisplayName("null attachment — hasAttachment false")
        void noAttachment() {
            var s = newSubmission(SubmissionStatus.SUBMITTED, null, null);
            assertThat(mapper.toSummary(s).hasAttachment()).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Submission newSubmission(SubmissionStatus status, Short grade, String textBody) {
        Submission s = new Submission();
        setField(s, "publicUuid", UUID.randomUUID());
        setField(s, "id", UUID.randomUUID());

        Task t = new Task();
        setField(t, "publicUuid", UUID.randomUUID());
        s.setTask(t);

        s.setStudentUserId(UUID.randomUUID());
        s.setSubmitterUserId(UUID.randomUUID());
        s.setTextBody(textBody);
        s.setStatus(status);
        s.setGrade(grade);
        s.setCreatedAt(Instant.parse("2026-06-10T10:00:00Z"));
        s.setUpdatedAt(Instant.parse("2026-06-10T10:00:00Z"));
        return s;
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