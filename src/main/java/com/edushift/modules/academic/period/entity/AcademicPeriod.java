package com.edushift.modules.academic.period.entity;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Academic period inside an {@link AcademicYear}. Anchors grade reports
 * (Sprint 7) and teacher assignments (BE-4.7).
 *
 * <h3>Invariants (enforced at the service layer)</h3>
 * <ul>
 *   <li>Ordinals are unique <em>and contiguous</em> per
 *       {@code (year, type)} — no gaps allowed.</li>
 *   <li>Date ranges within the same {@code (year, type)} MUST NOT
 *       overlap — checked with Postgres' {@code daterange &&}.</li>
 *   <li>{@code [startDate, endDate]} must lie inside
 *       {@code [year.startDate, year.endDate]}.</li>
 * </ul>
 */
@Entity
@Table(
		name = "academic_periods",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_academic_periods_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_academic_periods_tenant_year",
						columnList = "tenant_id, academic_year_id, period_type, ordinal")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "periodType", "ordinal", "name"})
@SQLDelete(sql = "UPDATE edushift.academic_periods "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class AcademicPeriod extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_academic_periods_year"))
	private AcademicYear academicYear;

	@Enumerated(EnumType.STRING)
	@Column(name = "period_type", nullable = false, length = 16)
	private PeriodType periodType;

	@Column(name = "ordinal", nullable = false)
	private Integer ordinal;

	@Column(name = "name", nullable = false, length = 60)
	private String name;

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
