package com.edushift.modules.quizzes.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * <strong>QuizAnswer</strong> — a single answer in a
 * {@link QuizAttempt} for a {@link QuizQuestion}
 * (Sprint 7b / BE-7b.0).
 *
 * <h3>Mutual exclusivity of payload columns (DB-enforced)</h3>
 * <ul>
 *   <li>MC: {@code selected_option_id} NOT NULL, others NULL.</li>
 *   <li>TF: {@code selected_boolean} NOT NULL, others NULL.</li>
 *   <li>SHORT_ANSWER: {@code text_answer} NOT NULL, others NULL.</li>
 * </ul>
 *
 * <h3>Invariants (DB-enforced)</h3>
 * <ul>
 *   <li>{@code (attempt_id, question_id)} is unique (UNIQUE). The
 *       same question within an attempt has a single row; the row
 *       is UPDATED on re-save (autosave pattern).</li>
 *   <li>{@code points_awarded} NULL or in [0, 100].</li>
 *   <li>Soft-delete via {@code @SQLDelete}.</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * Auto-filtered by Hibernate's {@code @TenantId} discriminator.
 */
@Entity
@Table(
		name = "lms_quiz_answers",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_lms_quiz_answers_public_uuid",
						columnNames = "public_uuid"),
				@UniqueConstraint(name = "uq_lms_quiz_answers_attempt_question",
						columnNames = {"attempt_id", "question_id"})
		},
		indexes = {
				@Index(name = "idx_lms_quiz_answers_tenant_attempt",
						columnList = "tenant_id, attempt_id"),
				@Index(name = "idx_lms_quiz_answers_tenant_pending",
						columnList = "tenant_id, graded_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "correct", "pointsAwarded"})
@SQLDelete(sql = "UPDATE edushift.lms_quiz_answers "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class QuizAnswer extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attempt_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_quiz_answers_attempt"))
	private QuizAttempt attempt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_quiz_answers_question"))
	private QuizQuestion question;

	/**
	 * MC only. {@code public_uuid} of the chosen option
	 * (soft FK to {@code lms_quiz_options.public_uuid}).
	 */
	@Column(name = "selected_option_id", columnDefinition = "uuid")
	private UUID selectedOptionId;

	/**
	 * TF only. Student's true/false answer.
	 */
	@Column(name = "selected_boolean")
	private Boolean selectedBoolean;

	/**
	 * SHORT_ANSWER only. Free-form text up to 5000 chars (DTO cap).
	 */
	@Column(name = "text_answer", columnDefinition = "text")
	private String textAnswer;

	@Column(name = "points_awarded")
	private Short pointsAwarded;

	@Column(name = "is_correct")
	private Boolean correct;

	@Column(name = "graded_by_user_id", columnDefinition = "uuid")
	private UUID gradedByUserId;

	@Column(name = "graded_at")
	private Instant gradedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
