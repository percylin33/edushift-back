package com.edushift.modules.academic.levelgrade.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * Fine-grained progression inside an {@link AcademicLevel} (e.g.
 * "1ro Primaria", "2do Primaria", ...). Tenant-aware.
 *
 * <p>{@code (tenant_id, level_id, ordinal)} is unique on non-deleted
 * rows ({@code uk_grades_level_ordinal_active}). Reorder is implemented
 * via a dedicated bulk endpoint that swaps ordinals atomically (see
 * {@code GradeService#reorder}).</p>
 */
@Entity
@Table(
		name = "grades",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_grades_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_grades_tenant_level_ordinal",
						columnList = "tenant_id, level_id, ordinal")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "name", "ordinal"})
@SQLDelete(sql = "UPDATE edushift.grades "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Grade extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "level_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @jakarta.persistence.ForeignKey(name = "fk_grades_level"))
	private AcademicLevel level;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "ordinal", nullable = false)
	private Integer ordinal;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
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
