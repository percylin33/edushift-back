package com.edushift.modules.schedule.daytemplate.entity;

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
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Non-teaching (or specialist-reserved) window inside a
 * {@link DayScheduleTemplate}. {@code dayOfWeek == null} means every day.
 */
@Entity
@Table(
		name = "day_schedule_blocks",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_day_schedule_blocks_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_day_schedule_blocks_template",
						columnList = "tenant_id, template_id, start_time")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "blockType", "startTime", "endTime"})
@SQLDelete(sql = "UPDATE edushift.day_schedule_blocks "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
@SQLRestriction("deleted = false")
public class DayScheduleBlock extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "template_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_day_schedule_blocks_template"))
	private DayScheduleTemplate template;

	/** ISO-8601 day of week 1=MON..7=SUN; null = all days. */
	@Column(name = "day_of_week")
	private Short dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "block_type", nullable = false, length = 32)
	private DayBlockType blockType;

	@Column(name = "label", nullable = false, length = 80)
	private String label;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (label != null) {
			label = label.trim();
		}
	}

	/** Delegates to {@link DayBlockType#isHardNonTeaching()}. */
	public boolean isHardNonTeaching() {
		return blockType != null && blockType.isHardNonTeaching();
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
