package com.edushift.modules.academic.year.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
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
 * Academic year within a tenant institution.
 *
 * <h3>Identifiers</h3>
 * <ul>
 *   <li>{@code id} (inherited) — internal UUIDv7, FK target inside the DB.</li>
 *   <li>{@code publicUuid} — UUIDv4 surfaced via REST.</li>
 *   <li>{@code name} — human label such as {@code "2026"}; unique per tenant
 *       case-insensitive on non-deleted rows.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * See {@link AcademicYearStatus}. The DB enforces the "single ACTIVE per
 * tenant" invariant via {@code uk_academic_years_tenant_active}.
 *
 * <h3>Calendar window</h3>
 * {@code startDate < endDate} (CHECK constraint). Periods (V17) and
 * student enrollments anchor inside this window.
 */
@Entity
@Table(
		name = "academic_years",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_academic_years_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_academic_years_tenant_status",
						columnList = "tenant_id, status"),
				@Index(name = "idx_academic_years_tenant_dates",
						columnList = "tenant_id, start_date, end_date")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "name", "status", "startDate", "endDate"})
@SQLDelete(sql = "UPDATE edushift.academic_years "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class AcademicYear extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private AcademicYearStatus status = AcademicYearStatus.PLANNING;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = AcademicYearStatus.PLANNING;
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
