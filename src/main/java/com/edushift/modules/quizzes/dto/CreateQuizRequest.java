package com.edushift.modules.quizzes.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * JSON body for {@code POST /sections/{uuid}/quizzes}
 * (Sprint 7b / BE-7b.1).
 *
 * <p>Scope aligned with the current {@code lms_quizzes} schema
 * (migration {@code V35__create_lms_quizzes.sql}, BE-7b.0):
 * <ul>
 *   <li>{@code maxScore} is an integer 0..1000 (column
 *       {@code max_score} is {@code smallint}).</li>
 *   <li>{@code timeLimitMinutes} is nullable; when present 1..480
 *       (DB CHECK).</li>
 *   <li>{@code maxAttempts} is 1..10 (DB CHECK).</li>
 *   <li>{@code dueAt} is nullable but if present must be in the
 *       future; the DB does not enforce this, so the service
 *       validates it explicitly.</li>
 * </ul>
 *
 * <p>The {@code questions} block is optional on creation: a teacher
 * can create a shell quiz and add questions later. If supplied,
 * each question is validated against the same invariants as
 * {@code addQuestion}.
 *
 * <p><strong>DEBT-BE-7B-4</strong> — extended fields
 * ({@code availableAt}, {@code instructions}, etc.) are not
 * modelled in the entity yet. The DTO intentionally omits them so
 * the API contract is honest. They will be added in a later
 * 7b.x once a migration extends the table.
 */
public record CreateQuizRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 10000) String description,
		@Future Instant dueAt,
		@Min(1) @Max(480) Integer timeLimitMinutes,
		@NotNull @Min(1) @Max(10) Integer maxAttempts,
		@NotNull @Min(0) @Max(1000) Integer maxScore,
		List<CreateQuestionRequest> questions
) {
}
