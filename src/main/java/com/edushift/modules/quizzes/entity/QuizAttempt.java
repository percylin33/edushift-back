package com.edushift.modules.quizzes.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * <strong>QuizAttempt</strong> — a student's attempt at a
 * {@link Quiz} (Sprint 7b / BE-7b.0).
 *
 * <h3>Invariants (DB-enforced)</h3>
 * <ul>
 *   <li>{@code (quiz_id, student_user_id, attempt_number)} is unique
 *       (UNIQUE). Re-attempts create a new row, not an update.</li>
 *   <li>{@code status} ∈ {IN_PROGRESS, SUBMITTED, AUTO_GRADED,
 *       GRADED, EXPIRED} (CHECK).</li>
 *   <li>{@code submitted_at} NULL iff {@code status = IN_PROGRESS}.</li>
 *   <li>For {@code status = GRADED}: {@code graded_by_user_id} and
 *       {@code graded_at} both NOT NULL.</li>
 *   <li>{@code score} / {@code auto_score} / {@code manual_score}
 *       either NULL or in [0, 1000].</li>
 *   <li>Soft-delete via {@code @SQLDelete} (orphan pattern: answers
 *       survive the parent attempt).</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * Auto-filtered by Hibernate's {@code @TenantId} discriminator.
 */
@Entity
@Table(
		name = "lms_quiz_attempts",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_lms_quiz_attempts_public_uuid",
						columnNames = "public_uuid"),
				@UniqueConstraint(name = "uq_lms_quiz_attempts_quiz_student_num",
						columnNames = {"quiz_id", "student_user_id", "attempt_number"})
		},
		indexes = {
				@Index(name = "idx_lms_quiz_attempts_tenant_student_created",
						columnList = "tenant_id, student_user_id, created_at"),
				@Index(name = "idx_lms_quiz_attempts_tenant_quiz_status",
						columnList = "tenant_id, quiz_id, status"),
				@Index(name = "idx_lms_quiz_attempts_tenant_expires",
						columnList = "tenant_id, expires_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "attemptNumber", "status", "score"})
@SQLDelete(sql = "UPDATE edushift.lms_quiz_attempts "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class QuizAttempt extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "quiz_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_quiz_attempts_quiz"))
	private Quiz quiz;

	@Column(name = "student_user_id", nullable = false, columnDefinition = "uuid")
	private UUID studentUserId;

	@Column(name = "submitter_user_id", nullable = false, columnDefinition = "uuid")
	private UUID submitterUserId;

	@Column(name = "attempt_number", nullable = false)
	private Short attemptNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private AttemptStatus status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "auto_score")
	private Short autoScore;

	@Column(name = "manual_score")
	private Short manualScore;

	@Column(name = "score")
	private Short score;

	@Column(name = "graded_by_user_id", columnDefinition = "uuid")
	private UUID gradedByUserId;

	@Column(name = "graded_at")
	private Instant gradedAt;

	@Column(name = "feedback", length = 2000)
	private String feedback;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = AttemptStatus.IN_PROGRESS;
		}
		if (startedAt == null) {
			startedAt = Instant.now();
		}
	}
}
