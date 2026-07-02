package com.edushift.modules.tasks.submission.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Submission DTOs")
class SubmissionDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    // =====================================================================
    // CreateSubmissionRequest
    // =====================================================================

    @Nested
    @DisplayName("CreateSubmissionRequest")
    class CreateSubmissionRequestTests {

        @Test
        @DisplayName("valid with textBody")
        void validText() {
            var r = new CreateSubmissionRequest(UUID.randomUUID(), "mi tarea", null);
            assertThat(validator.validate(r)).isEmpty();
        }

        @Test
        @DisplayName("valid with attachment only")
        void validAttachment() {
            var r = new CreateSubmissionRequest(UUID.randomUUID(), null, UUID.randomUUID());
            assertThat(validator.validate(r)).isEmpty();
        }

        @Test
        @DisplayName("null studentPublicUuid — NotNull violation")
        void studentNull() {
            var r = new CreateSubmissionRequest(null, "body", null);
            assertThat(validator.validate(r)).isNotEmpty();
        }

        @Test
        @DisplayName("textBody over 50000 chars — Size violation")
        void textTooLong() {
            var r = new CreateSubmissionRequest(UUID.randomUUID(), "x".repeat(50001), null);
            Set<ConstraintViolation<CreateSubmissionRequest>> v = validator.validate(r);
            assertThat(v).isNotEmpty();
        }
    }

    // =====================================================================
    // GradeSubmissionRequest
    // =====================================================================

    @Nested
    @DisplayName("GradeSubmissionRequest")
    class GradeSubmissionRequestTests {

        @Test
        @DisplayName("valid grade=0 + feedback")
        void validZero() {
            var r = new GradeSubmissionRequest(0, "needs work");
            assertThat(validator.validate(r)).isEmpty();
        }

        @Test
        @DisplayName("valid grade=100 + feedback")
        void validHundred() {
            var r = new GradeSubmissionRequest(100, "excellent");
            assertThat(validator.validate(r)).isEmpty();
        }

        @Test
        @DisplayName("null grade — NotNull violation")
        void gradeNull() {
            var r = new GradeSubmissionRequest(null, "fb");
            assertThat(validator.validate(r)).isNotEmpty();
        }

        @Test
        @DisplayName("grade=-1 — Min violation")
        void gradeNegative() {
            var r = new GradeSubmissionRequest(-1, "fb");
            assertThat(validator.validate(r)).isNotEmpty();
        }

        @Test
        @DisplayName("grade=101 — Max violation")
        void gradeAboveHundred() {
            var r = new GradeSubmissionRequest(101, "fb");
            assertThat(validator.validate(r)).isNotEmpty();
        }

        @Test
        @DisplayName("feedback over 2000 chars — Size violation")
        void feedbackTooLong() {
            var r = new GradeSubmissionRequest(50, "x".repeat(2001));
            assertThat(validator.validate(r)).isNotEmpty();
        }

        @Test
        @DisplayName("null feedback — allowed")
        void feedbackNull() {
            var r = new GradeSubmissionRequest(50, null);
            assertThat(validator.validate(r)).isEmpty();
        }
    }

    // =====================================================================
    // SubmissionResponse
    // =====================================================================

    @Nested
    @DisplayName("SubmissionResponse")
    class SubmissionResponseTests {

        @Test
        @DisplayName("constructor + accessors")
        void construct() {
            UUID pub = UUID.randomUUID();
            UUID task = UUID.randomUUID();
            UUID student = UUID.randomUUID();
            UUID submitter = UUID.randomUUID();
            UUID gradedBy = UUID.randomUUID();
            UUID attach = UUID.randomUUID();
            Instant gradedAt = Instant.parse("2026-06-15T10:00:00Z");

            var r = new SubmissionResponse(
                    pub, task, student, submitter,
                    "body", attach, SubmissionStatus.GRADED,
                    95, "great", gradedBy, gradedAt,
                    Boolean.TRUE,
                    Instant.parse("2026-06-10T10:00:00Z"),
                    Instant.parse("2026-06-15T10:00:00Z"));

            assertThat(r.publicUuid()).isEqualTo(pub);
            assertThat(r.taskPublicUuid()).isEqualTo(task);
            assertThat(r.studentPublicUuid()).isEqualTo(student);
            assertThat(r.submitterPublicUuid()).isEqualTo(submitter);
            assertThat(r.textBody()).isEqualTo("body");
            assertThat(r.attachmentPublicUuid()).isEqualTo(attach);
            assertThat(r.status()).isEqualTo(SubmissionStatus.GRADED);
            assertThat(r.grade()).isEqualTo(95);
            assertThat(r.feedback()).isEqualTo("great");
            assertThat(r.gradedByPublicUuid()).isEqualTo(gradedBy);
            assertThat(r.gradedAt()).isEqualTo(gradedAt);
            assertThat(r.wasIdempotent()).isTrue();
        }
    }

    // =====================================================================
    // SubmissionSummary
    // =====================================================================

    @Nested
    @DisplayName("SubmissionSummary")
    class SubmissionSummaryTests {

        @Test
        @DisplayName("constructor + accessors")
        void construct() {
            UUID pub = UUID.randomUUID();
            UUID student = UUID.randomUUID();
            var s = new SubmissionSummary(
                    pub, student, SubmissionStatus.SUBMITTED, null,
                    false, Instant.parse("2026-06-10T10:00:00Z"));
            assertThat(s.publicUuid()).isEqualTo(pub);
            assertThat(s.studentPublicUuid()).isEqualTo(student);
            assertThat(s.status()).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(s.grade()).isNull();
            assertThat(s.hasAttachment()).isFalse();
        }

        @Test
        @DisplayName("graded + hasAttachment")
        void gradedWithAttachment() {
            var s = new SubmissionSummary(
                    UUID.randomUUID(), UUID.randomUUID(),
                    SubmissionStatus.GRADED, 90, true, Instant.now());
            assertThat(s.status()).isEqualTo(SubmissionStatus.GRADED);
            assertThat(s.grade()).isEqualTo(90);
            assertThat(s.hasAttachment()).isTrue();
        }
    }
}