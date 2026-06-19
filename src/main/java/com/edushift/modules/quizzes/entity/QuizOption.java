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
 * <strong>QuizOption</strong> — answer option for a
 * {@link QuestionType#MC MC} {@link QuizQuestion} (Sprint 7b / BE-7b.0).
 *
 * <p>TF and SHORT_ANSWER questions have <strong>zero</strong> rows
 * in this table. For MC, exactly one option per question has
 * {@code is_correct = true}; this invariant is enforced at the DB
 * level by the trigger {@code enforce_one_correct_mc_option}
 * (see {@code V35__create_lms_quizzes.sql}).
 *
 * <h3>Invariants (DB-enforced)</h3>
 * <ul>
 *   <li>{@code (question_id, position)} is unique (UNIQUE).</li>
 *   <li>{@code position} >= 1; {@code label} non-blank (CHECK).</li>
 *   <li>Soft-delete via {@code @SQLDelete}.</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * Auto-filtered by Hibernate's {@code @TenantId} discriminator.
 */
@Entity
@Table(
		name = "lms_quiz_options",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_lms_quiz_options_public_uuid",
						columnNames = "public_uuid"),
				@UniqueConstraint(name = "uq_lms_quiz_options_question_position",
						columnNames = {"question_id", "position"})
		},
		indexes = {
				@Index(name = "idx_lms_quiz_options_tenant_question_position",
						columnList = "tenant_id, question_id, position")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "position", "label", "correct"})
@SQLDelete(sql = "UPDATE edushift.lms_quiz_options "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class QuizOption extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_quiz_options_question"))
	private QuizQuestion question;

	@Column(name = "position", nullable = false)
	private Short position;

	@Column(name = "label", nullable = false, length = 500)
	private String label;

	@Column(name = "is_correct", nullable = false)
	private boolean correct = false;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
