package com.edushift.modules.academic.course.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
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
 * Course catalog item inside a tenant (Sprint 4 / BE-4.4).
 *
 * <p>The {@code course → level} relationship is M:N via the explicit
 * {@link CourseLevel} pivot. The pivot is the source of truth — there
 * is no {@code @ManyToMany} on this entity to keep loading explicit
 * and avoid N+1 surprises.</p>
 *
 * <h3>Lifecycle</h3>
 * Courses are <strong>deactivated</strong> ({@code is_active=false})
 * rather than deleted whenever a teacher assignment / grade report is
 * already referencing them. Soft-delete is reserved for true mistakes
 * (created with the wrong code, etc.).
 */
@Entity
@Table(
		name = "courses",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_courses_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_courses_tenant_active_name",
						columnList = "tenant_id, is_active, name")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "code", "name", "isActive"})
@SQLDelete(sql = "UPDATE edushift.courses "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Course extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "credits")
	private Integer credits;

	@Column(name = "hours_per_week")
	private Integer hoursPerWeek;

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
