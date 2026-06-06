package com.edushift.modules.academic.levelgrade.entity;

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
 * Coarse education stage inside a tenant (INICIAL / PRIMARIA / SECUNDARIA
 * by default; extensible per tenant via custom levels like
 * {@code IGCSE} or {@code IB_DIPLOMA}).
 *
 * <p>Seeded by {@code AcademicSeedService} the first time a tenant is
 * created (Sprint 4 / BE-4.2). After seeding, the catalog is fully
 * editable through {@code /v1/academic/levels}.</p>
 *
 * <p>{@code code} is the stable identifier (uppercase, snake_case)
 * exposed to API consumers and used by seeding to detect the level the
 * grade defaults belong to. {@code name} is the human label shown in
 * the UI.</p>
 */
@Entity
@Table(
		name = "academic_levels",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_academic_levels_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_academic_levels_tenant_ordinal",
						columnList = "tenant_id, ordinal")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "code", "name", "ordinal"})
@SQLDelete(sql = "UPDATE edushift.academic_levels "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class AcademicLevel extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

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
		normalizeCode();
	}

	@PreUpdate
	private void onPreUpdate() {
		normalizeCode();
	}

	private void normalizeCode() {
		if (code != null) {
			code = code.trim().toUpperCase();
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
