package com.edushift.modules.schedule.daytemplate.entity;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
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
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * School-day structure (recess / lunch / etc.) for a year + level,
 * with optional grade override (V103 / ADR-SCH-6).
 */
@Entity
@Table(
		name = "day_schedule_templates",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_day_schedule_templates_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_day_schedule_templates_tenant_year",
						columnList = "tenant_id, academic_year_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "name", "shift"})
@SQLDelete(sql = "UPDATE edushift.day_schedule_templates "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class DayScheduleTemplate extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_day_schedule_templates_year"))
	private AcademicYear academicYear;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_level_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_day_schedule_templates_level"))
	private AcademicLevel academicLevel;

	/** When non-null, overrides the level-default template for this grade. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "grade_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_day_schedule_templates_grade"))
	private Grade grade;

	/** {@code MORNING} / {@code AFTERNOON}, or null when shift-agnostic. */
	@Column(name = "shift", length = 20)
	private String shift;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	/**
	 * Optional share-group key for {@link RecessPolicy#SHARED} tenants
	 * (levels that must keep identical recess windows).
	 */
	@Column(name = "recess_share_group", length = 80)
	private String recessShareGroup;

	/** School-day start (entrada). Used to suggest academic periods (ADR-SCH-12). */
	@Column(name = "day_start")
	private LocalTime dayStart;

	/** School-day end (salida). */
	@Column(name = "day_end")
	private LocalTime dayEnd;

	/** Default academic period length in minutes. */
	@Column(name = "period_minutes")
	private Integer periodMinutes;

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
		if (shift != null) {
			shift = shift.trim().toUpperCase();
			if (shift.isEmpty()) {
				shift = null;
			}
		}
		if (recessShareGroup != null && recessShareGroup.isBlank()) {
			recessShareGroup = null;
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
