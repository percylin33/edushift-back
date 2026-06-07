package com.edushift.modules.academic.unit.entity;

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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * Pedagogical unit inside a course (Sprint 5A / BE-5A.1).
 *
 * <p>Units are the bridge between {@link Course} and
 * {@code LearningSession}. Each session anchors on exactly one unit
 * (BE-5A.4). Units are tenant-scoped via the discriminator on
 * {@link TenantAwareEntity}.</p>
 *
 * <h3>Lifecycle</h3>
 * Units are <strong>deactivated</strong> ({@code is_active=false})
 * rather than deleted whenever a session is already referencing them.
 * Hard soft-delete is reserved for true mistakes; the service emits
 * {@code UNIT_HAS_SESSIONS} (409) if a non-empty unit is targeted.
 */
@Entity
@Table(
		name = "academic_units",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_academic_units_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_academic_units_tenant_course_active",
						columnList = "tenant_id, course_id, is_active, display_order")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "name", "displayOrder", "isActive"})
@SQLDelete(sql = "UPDATE edushift.academic_units "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Unit extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_academic_units_course"))
	private Course course;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "display_order", nullable = false)
	private Integer displayOrder;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

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
