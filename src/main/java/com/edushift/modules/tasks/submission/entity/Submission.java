package com.edushift.modules.tasks.submission.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import com.edushift.modules.tasks.entity.Task;
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
 * <strong>Submission</strong> — current submission per
 * {@code (task, student)} pair (Sprint 7a / BE-7a.2). Re-submits
 * overwrite this row in-place; the previous payload is preserved
 * in {@link SubmissionRevision}.
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code (task, student)} is unique on non-deleted rows
 *       (DB-enforced via {@code uq_lms_submissions_task_student},
 *       D-TSK-01).</li>
 *   <li>{@code status=GRADED} requires {@code grade} (0..100) and
 *       non-null {@code gradedByUserId} + {@code gradedAt}
 *       (DB CHECKs {@code chk_lms_submissions_grade_range} and
 *       {@code chk_lms_submissions_graded_consistent}).</li>
 *   <li>At least one of {@code textBody} / {@code attachmentPublicUuid}
 *       must be non-null (DB CHECK
 *       {@code chk_lms_submissions_payload_not_empty}).</li>
 *   <li>{@code studentUserId} ≠ {@code submitterUserId} only in
 *       the parent-on-behalf flow. For self-submit they match.</li>
 * </ul>
 */
@Entity
@Table(
		name = "lms_submissions",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_lms_submissions_public_uuid",
						columnNames = "public_uuid"),
				@UniqueConstraint(name = "uq_lms_submissions_task_student",
						columnNames = {"task_id", "student_user_id"})
		},
		indexes = {
				@Index(name = "idx_lms_submissions_tenant_task",
						columnList = "tenant_id, task_id"),
				@Index(name = "idx_lms_submissions_tenant_student",
						columnList = "tenant_id, student_user_id, created_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "status", "grade"})
@SQLDelete(sql = "UPDATE edushift.lms_submissions "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Submission extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_submissions_task"))
	private Task task;

	@Column(name = "student_user_id", nullable = false, columnDefinition = "uuid")
	private UUID studentUserId;

	@Column(name = "submitter_user_id", nullable = false, columnDefinition = "uuid")
	private UUID submitterUserId;

	@Column(name = "text_body", columnDefinition = "text")
	private String textBody;

	@Column(name = "attachment_public_uuid", columnDefinition = "uuid")
	private UUID attachmentPublicUuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private SubmissionStatus status;

	@Column(name = "grade")
	private Short grade;

	@Column(name = "feedback", length = 2000)
	private String feedback;

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
		if (status == null) {
			status = SubmissionStatus.SUBMITTED;
		}
	}
}
