package com.edushift.modules.schedule.timeslot.entity;

import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
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
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * Weekly recurring time slot tied to a {@link TeacherAssignment}
 * (Sprint 5A / BE-5A.3).
 *
 * <p>One slot = "Mondays 08:00–09:00, classroom 12". The slot does not
 * carry any date; concrete occurrences are {@code learning_sessions}
 * (BE-5A.4).</p>
 *
 * <h3>Lifecycle</h3>
 * Hard soft-delete only (no {@code is_active} flag). To "pause" a slot
 * the FE removes it and the admin re-creates it later. Rationale: a
 * weekly pattern doesn't have a meaningful "deactivated but kept"
 * state; the assignment's lifecycle (soft-end) is the right place to
 * retire all slots together.
 */
@Entity
@Table(
		name = "time_slots",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_time_slots_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_time_slots_tenant_assignment_day",
						columnList = "tenant_id, teacher_assignment_id, day_of_week, start_time"),
				@Index(name = "idx_time_slots_tenant_day",
						columnList = "tenant_id, day_of_week, start_time")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "dayOfWeek", "startTime", "endTime", "classroom"})
@SQLDelete(sql = "UPDATE edushift.time_slots "
		+ "SET deleted = true, updated_at = NOW() "
		+ "WHERE id = ?")
public class TimeSlot extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_assignment_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_time_slots_assignment"))
	private TeacherAssignment teacherAssignment;

	/** ISO-8601 day of week: 1=MON, 2=TUE, ..., 7=SUN. */
	@Column(name = "day_of_week", nullable = false)
	private Short dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "classroom", length = 80)
	private String classroom;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
