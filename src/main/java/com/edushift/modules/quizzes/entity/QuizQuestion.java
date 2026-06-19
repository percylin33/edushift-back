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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * <strong>QuizQuestion</strong> — question bank row for a
 * {@link Quiz} (Sprint 7b / BE-7b.0).
 *
 * <h3>Invariants (DB-enforced)</h3>
 * <ul>
 *   <li>{@code question_type} is one of {@link QuestionType#MC MC},
 *       {@link QuestionType#TF TF},
 *       {@link QuestionType#SHORT_ANSWER SHORT_ANSWER} (CHECK).</li>
 *   <li>{@code (quiz_id, position)} is unique (UNIQUE).</li>
 *   <li>For {@code TF}: {@code correct_boolean} NOT NULL.</li>
 *   <li>For {@code MC} / {@code SHORT_ANSWER}: {@code correct_boolean} IS NULL.</li>
 *   <li>For {@code SHORT_ANSWER}: {@code expected_keywords} may be non-NULL.</li>
 *   <li>For {@code MC} / {@code TF}: {@code expected_keywords} IS NULL.</li>
 *   <li>{@code points} in [1, 100]; {@code position} >= 1.</li>
 *   <li>Soft-delete via {@code @SQLDelete} (orphan pattern: answers
 *       survive the parent question).</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * Auto-filtered by Hibernate's {@code @TenantId} discriminator.
 */
@Entity
@Table(
		name = "lms_quiz_questions",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_lms_quiz_questions_public_uuid",
						columnNames = "public_uuid"),
				@UniqueConstraint(name = "uq_lms_quiz_questions_quiz_position",
						columnNames = {"quiz_id", "position"})
		},
		indexes = {
				@Index(name = "idx_lms_quiz_questions_tenant_quiz_position",
						columnList = "tenant_id, quiz_id, position")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "position", "questionType"})
@SQLDelete(sql = "UPDATE edushift.lms_quiz_questions "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class QuizQuestion extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "quiz_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_quiz_questions_quiz"))
	private Quiz quiz;

	@Column(name = "position", nullable = false)
	private Short position;

	@Enumerated(EnumType.STRING)
	@Column(name = "question_type", nullable = false, length = 16)
	private QuestionType questionType;

	@Column(name = "prompt", nullable = false, columnDefinition = "text")
	private String prompt;

	@Column(name = "points", nullable = false)
	private Short points = 1;

	/**
	 * SHORT_ANSWER only. PostgreSQL {@code text[]} column mapped via
	 * {@link SqlTypes#ARRAY}. NULL for MC/TF.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "expected_keywords", columnDefinition = "text[]")
	private String[] expectedKeywords;

	/**
	 * TF only. NULL for MC/SHORT_ANSWER. Persisted as {@code BOOLEAN}.
	 */
	@Column(name = "correct_boolean")
	private Boolean correctBoolean;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
