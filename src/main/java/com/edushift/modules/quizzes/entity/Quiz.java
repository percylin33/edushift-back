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
 * <strong>Quiz</strong> — per-section LMS quiz header
 * (Sprint 7b / BE-7b.0).
 *
 * <p>Questions live in {@link QuizQuestion} (which in turn has
 * {@link QuizOption} for MC). Student attempts live in
 * {@link QuizAttempt}.
 *
 * <h3>Invariants (DB-enforced)</h3>
 * <ul>
 *   <li>{@code status} is one of {@link QuizStatus#DRAFT DRAFT},
 *       {@link QuizStatus#PUBLISHED PUBLISHED},
 *       {@link QuizStatus#CLOSED CLOSED} (CHECK).</li>
 *   <li>{@code published_at} is non-NULL iff {@code status >= PUBLISHED}.</li>
 *   <li>{@code closed_at} is non-NULL iff {@code status = CLOSED}.</li>
 *   <li>{@code max_score} in [0, 1000]; {@code time_limit_minutes}
 *       either NULL or in [1, 480]; {@code attempts_allowed}
 *       in [1, 10].</li>
 *   <li>Soft-delete via {@code @SQLDelete} + base
 *       {@code @SQLRestriction("deleted = false")} (orphan pattern:
 *       attempts survive the parent quiz).</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * Auto-filtered by Hibernate's {@code @TenantId} discriminator; no
 * query in the repository or service writes raw SQL bypassing it.
 */
@Entity
@Table(
		name = "lms_quizzes",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_lms_quizzes_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_lms_quizzes_tenant_section_due",
						columnList = "tenant_id, section_id, due_at"),
				@Index(name = "idx_lms_quizzes_tenant_owner_created",
						columnList = "tenant_id, owner_user_id, created_at"),
				@Index(name = "idx_lms_quizzes_tenant_status_due",
						columnList = "tenant_id, status, due_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "title", "status"})
@SQLDelete(sql = "UPDATE edushift.lms_quizzes "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Quiz extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_quizzes_section"))
	private com.edushift.modules.academic.section.entity.Section section;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private QuizStatus status;

	@Column(name = "max_score", nullable = false)
	private Short maxScore = 100;

	@Column(name = "due_at")
	private Instant dueAt;

	@Column(name = "time_limit_minutes")
	private Short timeLimitMinutes;

	@Column(name = "attempts_allowed", nullable = false)
	private Short attemptsAllowed = 1;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "owner_user_id", nullable = false, columnDefinition = "uuid")
	private UUID ownerUserId;

	/**
	 * Optional FK to a {@link com.edushift.modules.evaluations.rubric.entity.Rubric}
	 * (Sprint 7b / BE-7b.3). NULL → the quiz is graded numerically only
	 * (the BE-7b.0..2 behaviour). When non-null, the teacher can apply
	 * qualitative criteria via {@code gradeWithRubric}, which writes an
	 * additional {@code GradeRecord} anchored to
	 * {@link #rubricEvaluation}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rubric_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_quizzes_rubric"))
	private com.edushift.modules.evaluations.rubric.entity.Rubric rubric;

	/**
	 * Derived anchor evaluation created by {@code QuizService.attachRubric}
	 * the first time {@link #rubric} is set. Holds the
	 * {@code teacherAssignment} of the quiz's owner on the quiz's
	 * section (BE-7b.3 decision A1: "reuse the owner's first active
	 * assignment in the same section"). The {@code grade_records}
	 * produced by {@code gradeWithRubric} reference this evaluation
	 * through the standard {@code grade_records.evaluation_id} FK.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rubric_evaluation_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_quizzes_rubric_evaluation"))
	private com.edushift.modules.evaluations.entity.Evaluation rubricEvaluation;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = QuizStatus.DRAFT;
		}
	}
}
