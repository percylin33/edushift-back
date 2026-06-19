package com.edushift.modules.tasks.submission.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * <strong>SubmissionRevision</strong> — append-only audit row that
 * snapshots the previous {@link Submission} payload BEFORE a
 * re-submit (Sprint 7a / BE-7a.2, D-TSK-01).
 *
 * <p>One revision per re-submit. {@code revisionNumber} is 1-based
 * and monotonic per {@code submission_id}; the service computes
 * {@code MAX(revisionNumber) + 1} inside the same transaction as
 * the re-submit.
 *
 * <p>Extends {@link TenantAwareEntity} so the {@code tenant_id}
 * column is auto-populated by Hibernate's {@code @TenantId}
 * discriminator from {@code TenantContext}. The row inherits the
 * same tenant as its parent {@link Submission}; this also lets
 * the {@code @TenantId} filter apply to direct repository
 * queries against this table.
 */
@Entity
@Table(
		name = "lms_submission_revisions",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_lms_submission_revisions_submission_number",
						columnNames = {"submission_id", "revision_number"})
		},
		indexes = {
				@Index(name = "idx_lms_submission_revisions_tenant_submission",
						columnList = "tenant_id, submission_id, revision_number")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"revisionNumber"})
public class SubmissionRevision extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "submission_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_submission_revisions_submission"))
	private Submission submission;

	@Column(name = "revision_number", nullable = false)
	private short revisionNumber;

	@Column(name = "text_body", columnDefinition = "text")
	private String textBody;

	@Column(name = "attachment_public_uuid", columnDefinition = "uuid")
	private UUID attachmentPublicUuid;

	@Column(name = "created_by_user_id", nullable = false, columnDefinition = "uuid")
	private UUID createdByUserId;
}
