package com.edushift.modules.academic.course.entity;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
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
 * Explicit M:N pivot between {@link Course} and
 * {@link com.edushift.modules.academic.levelgrade.entity.AcademicLevel}.
 *
 * <p>An explicit entity (instead of a {@code @ManyToMany} JoinTable)
 * keeps tenant scoping uniform — every row carries {@code tenant_id}
 * managed by Hibernate's {@code @TenantId} discriminator and benefits
 * from the audit machinery in {@code BaseEntity}.</p>
 */
@Entity
@Table(
		name = "course_levels",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_course_levels_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_course_levels_tenant_level",
						columnList = "tenant_id, level_id"),
				@Index(name = "idx_course_levels_course",
						columnList = "course_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid"})
@SQLDelete(sql = "UPDATE edushift.course_levels "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class CourseLevel extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_course_levels_course"))
	private Course course;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "level_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_course_levels_level"))
	private AcademicLevel level;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
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
