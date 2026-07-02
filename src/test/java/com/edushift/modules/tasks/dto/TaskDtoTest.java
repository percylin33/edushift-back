package com.edushift.modules.tasks.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Task DTOs")
class TaskDtoTest {

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
    // CreateTaskRequest
    // =====================================================================

    @Nested
    @DisplayName("CreateTaskRequest")
    class CreateTaskRequestTests {

        @Test
        @DisplayName("valid future payload — no violations")
        void valid() {
            var req = new CreateTaskRequest(
                    "Algebra Tarea 1",
                    "Resolver ejercicios",
                    Instant.now().plus(2, ChronoUnit.DAYS),
                    UUID.randomUUID());
            assertThat(validator.validate(req)).isEmpty();
        }

        @Test
        @DisplayName("blank title — NotBlank violation")
        void blankTitle() {
            var req = new CreateTaskRequest(
                    "   ",
                    "desc",
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null);
            Set<ConstraintViolation<CreateTaskRequest>> v = validator.validate(req);
            assertThat(v).extracting(c -> c.getPropertyPath().toString())
                    .contains("title");
        }

        @Test
        @DisplayName("null title — NotBlank violation")
        void nullTitle() {
            var req = new CreateTaskRequest(
                    null,
                    "desc",
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null);
            assertThat(validator.validate(req)).isNotEmpty();
        }

        @Test
        @DisplayName("title over 200 chars — Size violation")
        void titleTooLong() {
            var req = new CreateTaskRequest(
                    "x".repeat(201),
                    "desc",
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null);
            assertThat(validator.validate(req)).isNotEmpty();
        }

        @Test
        @DisplayName("description over 10000 chars — Size violation")
        void descriptionTooLong() {
            var req = new CreateTaskRequest(
                    "T",
                    "d".repeat(10001),
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    null);
            assertThat(validator.validate(req)).isNotEmpty();
        }

        @Test
        @DisplayName("dueAt in the past — Future violation")
        void dueAtPast() {
            var req = new CreateTaskRequest(
                    "T",
                    null,
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    null);
            assertThat(validator.validate(req)).isNotEmpty();
        }

        @Test
        @DisplayName("dueAt null — allowed (optional)")
        void dueAtNullAllowed() {
            var req = new CreateTaskRequest("T", null, null, null);
            assertThat(validator.validate(req)).isEmpty();
        }
    }

    // =====================================================================
    // TaskResponse
    // =====================================================================

    @Nested
    @DisplayName("TaskResponse")
    class TaskResponseTests {

        @Test
        @DisplayName("constructor + accessors")
        void construct() {
            UUID task = UUID.randomUUID();
            UUID sec = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            UUID attach = UUID.randomUUID();
            Instant now = Instant.parse("2026-06-01T10:00:00Z");

            var resp = new TaskResponse(
                    task, sec, "title", "desc",
                    now.plus(1, ChronoUnit.DAYS), attach, owner,
                    true, now, now);

            assertThat(resp.publicUuid()).isEqualTo(task);
            assertThat(resp.sectionPublicUuid()).isEqualTo(sec);
            assertThat(resp.title()).isEqualTo("title");
            assertThat(resp.description()).isEqualTo("desc");
            assertThat(resp.dueAt()).isEqualTo(now.plus(1, ChronoUnit.DAYS));
            assertThat(resp.attachmentPublicUuid()).isEqualTo(attach);
            assertThat(resp.ownerPublicUuid()).isEqualTo(owner);
            assertThat(resp.allowResubmission()).isTrue();
            assertThat(resp.createdAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("null sectionPublicUuid allowed")
        void nullSection() {
            var resp = new TaskResponse(
                    UUID.randomUUID(), null, "t", null, null,
                    null, UUID.randomUUID(), false, Instant.now(), Instant.now());
            assertThat(resp.sectionPublicUuid()).isNull();
        }
    }

    // =====================================================================
    // TaskSummary
    // =====================================================================

    @Nested
    @DisplayName("TaskSummary")
    class TaskSummaryTests {

        @Test
        @DisplayName("constructor + accessors")
        void construct() {
            UUID id = UUID.randomUUID();
            Instant due = Instant.parse("2026-06-10T00:00:00Z");
            var sum = new TaskSummary(id, "T", due, true, UUID.randomUUID(), Instant.now());
            assertThat(sum.publicUuid()).isEqualTo(id);
            assertThat(sum.title()).isEqualTo("T");
            assertThat(sum.dueAt()).isEqualTo(due);
            assertThat(sum.hasAttachment()).isTrue();
        }

        @Test
        @DisplayName("hasAttachment=false when null")
        void noAttachment() {
            var sum = new TaskSummary(UUID.randomUUID(), "T", null, false, UUID.randomUUID(), Instant.now());
            assertThat(sum.hasAttachment()).isFalse();
        }
    }

    // =====================================================================
    // UpdateTaskRequest
    // =====================================================================

    @Nested
    @DisplayName("UpdateTaskRequest")
    class UpdateTaskRequestTests {

        @Test
        @DisplayName("all-null — passes (no @NotNull), the service rejects empty patches")
        void allNull() {
            var req = new UpdateTaskRequest(null, null, null, null, null);
            assertThat(validator.validate(req)).isEmpty();
        }

        @Test
        @DisplayName("valid partial update")
        void partial() {
            var req = new UpdateTaskRequest(
                    "new title",
                    "new desc",
                    Instant.now().plus(2, ChronoUnit.DAYS),
                    UUID.randomUUID(),
                    Boolean.FALSE);
            assertThat(validator.validate(req)).isEmpty();
        }

        @Test
        @DisplayName("title over 200 chars — Size violation")
        void titleTooLong() {
            var req = new UpdateTaskRequest("x".repeat(201), null, null, null, null);
            assertThat(validator.validate(req)).isNotEmpty();
        }

        @Test
        @DisplayName("description over 10000 chars — Size violation")
        void descriptionTooLong() {
            var req = new UpdateTaskRequest(null, "d".repeat(10001), null, null, null);
            assertThat(validator.validate(req)).isNotEmpty();
        }

        @Test
        @DisplayName("dueAt in the past — Future violation")
        void dueAtPast() {
            var req = new UpdateTaskRequest(null, null,
                    Instant.now().minus(1, ChronoUnit.HOURS), null, null);
            assertThat(validator.validate(req)).isNotEmpty();
        }
    }
}