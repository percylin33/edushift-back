package com.edushift.modules.academic.competency.entity;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * MINEDU-style competency hanging off a course (Sprint 5A / BE-5A.2).
 *
 * <p>A {@code Competency} groups one or more {@link Capacity} instances
 * and is referenced by {@code LearningSession} (BE-5A.4) via
 * {@code competency_ids[]}. The pair {@code (course_id, lower(code))} is
 * the human key (e.g. {@code "COMU_C1"} → "Lee diversos tipos de textos
 * escritos").</p>
 *
 * <h3>Lifecycle</h3>
 * Competencies are <strong>deactivated</strong> ({@code is_active=false})
 * rather than deleted whenever a session is already referencing them.
 * Hard soft-delete is reserved for true mistakes; the service emits
 * {@code COMPETENCY_IN_USE_BY_SESSIONS} (409) if a non-empty competency
 * is targeted (placeholder until BE-5A.4 wires up).
 */
@Entity
@Table(
		name = "competencies",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_competencies_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_competencies_tenant_course_active",
						columnList = "tenant_id, course_id, is_active, display_order")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "code", "name", "displayOrder", "isActive"})
@SQLDelete(sql = "UPDATE edushift.competencies "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Competency extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_competencies_course"))
	private Course course;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 300)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "display_order", nullable = false)
	private Integer displayOrder;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = Boolean.TRUE;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (isActive == null) {
			isActive = Boolean.TRUE;
		}
		normalise();
	}

	@PreUpdate
	private void onPreUpdate() {
		normalise();
	}

	private void normalise() {
		if (code != null) {
			code = code.trim().toUpperCase();
		}
		if (name != null) {
			name = name.trim();
		}
	}

	@Override
	public void markDeleted() {
		super.markDeleted();
		this.deletedAt = Instant.now();
	}

	@Override
	public void restore() {
		super.restore();
		this.deletedAt = null;
	}
}
