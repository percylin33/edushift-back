package com.edushift.modules.tasks.entity;

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
 * <strong>Task</strong> — per-section LMS assignment header
 * (Sprint 7a / BE-7a.2).
 *
 * <p>Submissions live in {@link com.edushift.modules.tasks.submission.entity.Submission}
 * and reference this task via {@code task_id}. Soft-deleting a task
 * does NOT cascade to submissions (D-TSK-05: orphan pattern).
 */
@Entity
@Table(
		name = "lms_tasks",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_lms_tasks_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_lms_tasks_tenant_section_due",
						columnList = "tenant_id, section_id, due_at"),
				@Index(name = "idx_lms_tasks_tenant_owner_created",
						columnList = "tenant_id, owner_user_id, created_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "title", "dueAt"})
@SQLDelete(sql = "UPDATE edushift.lms_tasks "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Task extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_tasks_section"))
	private com.edushift.modules.academic.section.entity.Section section;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "due_at")
	private Instant dueAt;

	@Column(name = "attachment_public_uuid", columnDefinition = "uuid")
	private UUID attachmentPublicUuid;

	@Column(name = "owner_user_id", nullable = false, columnDefinition = "uuid")
	private UUID ownerUserId;

	@Column(name = "allow_resubmission", nullable = false)
	private boolean allowResubmission = true;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
