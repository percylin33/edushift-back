package com.edushift.modules.attendance.entity;

import com.edushift.modules.academic.section.entity.Section;
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
 * <strong>AttendanceSession</strong> — open or closed slot during which
 * a teacher can scan students for a {@link Section}. Sprint 6 / BE-6.1.
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>Only one {@code ACTIVE} row per {@code (section, occurredOn, slot)}
 *       on non-deleted rows (DB-enforced via
 *       {@code uk_attendance_sessions_section_day_slot_active}).</li>
 *   <li>{@code closedAt}/{@code closedByUserId} are set together when the
 *       lifecycle transitions to {@code CLOSED} (DB CHECK
 *       {@code chk_attendance_sessions_closed_consistent}).</li>
 *   <li>{@code closedByUserId} stores the user's <strong>publicUuid</strong>
 *       (consistent with V29 hotfix; FK targets
 *       {@code users.public_uuid}).</li>
 * </ul>
 */
@Entity
@Table(
		name = "attendance_sessions",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_attendance_sessions_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_attendance_sessions_tenant_section_date",
						columnList = "tenant_id, section_id, occurred_on"),
				@Index(name = "idx_attendance_sessions_tenant_status_date",
						columnList = "tenant_id, status, occurred_on")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "occurredOn", "slot", "status"})
@SQLDelete(sql = "UPDATE edushift.attendance_sessions "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class AttendanceSession extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_attendance_sessions_section"))
	private Section section;

	@Column(name = "occurred_on", nullable = false)
	private LocalDate occurredOn;

	@Enumerated(EnumType.STRING)
	@Column(name = "slot", nullable = false, length = 16)
	private AttendanceSessionSlot slot;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private AttendanceSessionStatus status = AttendanceSessionStatus.ACTIVE;

	@Column(name = "closed_by_user_id", columnDefinition = "uuid")
	private UUID closedByUserId;

	@Column(name = "notes", length = 500)
	private String notes;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = AttendanceSessionStatus.ACTIVE;
		}
	}
}
