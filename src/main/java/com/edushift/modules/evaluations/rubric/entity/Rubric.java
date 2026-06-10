package com.edushift.modules.evaluations.rubric.entity;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/**
 * Per-tenant scoring template. Sprint 5B / BE-5B.2.
 *
 * <p>A {@code Rubric} carries a JSONB list of weighted {@code criteria}
 * and a JSONB list of achievement {@code levels} (2..4 items, default
 * canonical MINEDU). When a tenant wants a custom variant of a system
 * (MINEDU-seed) rubric, it forks the system rubric via
 * {@code POST /rubrics/{uuid}/fork} which produces a new row with
 * {@code parentRubric} pointing at the source and {@code isSystem = false}.</p>
 *
 * <h3>Identity</h3>
 * {@code publicUuid} is the externally-exposed id. Internally the table
 * uses a stable {@code UUID v7} (see V26 migration).
 *
 * <h3>Concurrency</h3>
 * Forks and edits are race-safe via the
 * {@code uk_rubrics_tenant_name_ci} unique index. Soft-delete is
 * allowed for tenant-owned rows only; system rows cannot be deleted
 * (enforced at the service layer).
 *
 * <h3>Multi-tenant</h3>
 * Tenant isolation is enforced by Hibernate's {@code @TenantId}
 * filter on {@link TenantAwareEntity}. The unique index on
 * {@code (tenant_id, lower(name))} provides a second layer of
 * protection.
 */
@Entity
@Table(
		name = "rubrics",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_rubrics_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_rubrics_tenant_active",
						columnList = "tenant_id, is_system, name"),
				@Index(name = "idx_rubrics_tenant_system",
						columnList = "tenant_id, name"),
				@Index(name = "idx_rubrics_tenant_parent",
						columnList = "tenant_id, parent_rubric_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "name", "isSystem", "isActive"})
@SQLDelete(sql = "UPDATE edushift.rubrics "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Rubric extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "name", nullable = false, length = 160)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	/**
	 * Weighted criteria. Stored as JSONB and validated by the service
	 * layer (1..10 items, sum of {@code weight} = 100.0,
	 * each weight in [0, 100]).
	 *
	 * <p>Shape: {@code List<Map<String, Object>>} where each map is one
	 * criterion with keys {@code key}, {@code name}, {@code description}
	 * (optional), {@code weight} (BigDecimal 0..100),
	 * {@code descriptors} (List&lt;Map&gt; with keys {@code level} +
	 * {@code text}).</p>
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "criteria", nullable = false, columnDefinition = "jsonb")
	private List<Map<String, Object>> criteria;

	/**
	 * Achievement levels (2..4 items). For canonical MINEDU rubrics the
	 * codes are the {@link RubricLevel} enum names; tenant-defined
	 * rubrics can use any string but uniqueness within the array is
	 * enforced.
	 *
	 * <p>Shape: {@code List<Map<String, Object>>} with keys
	 * {@code code} (String), {@code name} (String), {@code order}
	 * (Integer, optional).</p>
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "levels", nullable = false, columnDefinition = "jsonb")
	private List<Map<String, Object>> levels;

	@Column(name = "is_system", nullable = false)
	private Boolean isSystem = Boolean.FALSE;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_rubric_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_rubrics_parent"))
	private Rubric parentRubric;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = Boolean.TRUE;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/**
	 * Auto-assigns {@code publicUuid} on first persist when the caller
	 * (typically a test seeding through the repository) hasn't provided
	 * one. The service layer of system-rubric seeding ({@code
	 * RubricSeedServiceImpl#materializeSystemRubrics}) already sets
	 * {@code publicUuid} explicitly; this hook is the safety net.
	 */
	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (isSystem == null) {
			isSystem = Boolean.FALSE;
		}
		if (isActive == null) {
			isActive = Boolean.TRUE;
		}
	}
}
