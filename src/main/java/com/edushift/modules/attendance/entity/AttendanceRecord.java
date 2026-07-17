package com.edushift.modules.attendance.entity;

import com.edushift.modules.students.entity.Student;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * <strong>AttendanceRecord</strong> — single row per {@code (session,
 * student)} pair representing the outcome of a check-in (or a
 * materialized {@code ABSENT} after the session closes).
 * Sprint 6 / BE-6.1.
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code (session, student)} is unique on non-deleted rows
 *       (DB-enforced via {@code uk_attendance_records_session_student}).
 *       The service uses upsert semantics so re-scanning the same
 *       student returns the existing row with {@code wasIdempotent=true}
 *       (ADR-6.3).</li>
 *   <li>{@code PRESENT}/{@code LATE} require a non-null
 *       {@code scannedByUserId} (DB CHECK
 *       {@code chk_attendance_records_scanned_by_present}).</li>
 *   <li>{@code editedByUserId} and {@code editedAt} travel together
 *       (DB CHECK {@code chk_attendance_records_edited_consistent}).</li>
 *   <li>Audit FKs ({@code scannedByUserId}, {@code editedByUserId})
 *       reference {@code users.public_uuid} — consistent with V29
 *       hotfix on {@code grade_records.recorded_by_user_id}.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * Writes are accepted only when the parent {@link AttendanceSession}
 * is in {@code ACTIVE}. Closing the session materializes
 * {@code ABSENT} rows for non-scanned enrolled students (ADR-6.6).
 * Manual edits via {@code PUT /records/{id}} are subject to the 24h
 * window for {@code TEACHER}, unbounded for {@code TENANT_ADMIN}
 * (ADR-6.7).
 */
@Entity
@Table(
		name = "attendance_records",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_attendance_records_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_attendance_records_session",
						columnList = "session_id"),
				@Index(name = "idx_attendance_records_tenant_student_date",
						columnList = "tenant_id, student_id, occurred_at"),
				@Index(name = "idx_attendance_records_tenant_status_date",
						columnList = "tenant_id, status, occurred_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "status", "occurredAt"})
@SQLDelete(sql = "UPDATE edushift.attendance_records "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class AttendanceRecord extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_attendance_records_session"))
	private AttendanceSession session;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_attendance_records_student"))
	private Student student;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private AttendanceRecordStatus status;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "scanned_by_user_id", columnDefinition = "uuid")
	private UUID scannedByUserId;

	@Column(name = "edited_by_user_id", columnDefinition = "uuid")
	private UUID editedByUserId;

	@Column(name = "edited_at")
	private Instant editedAt;

	@Column(name = "notes", length = 500)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(name = "justification_status", length = 16)
	private JustificationStatus justificationStatus;

	@Column(name = "justification_text", length = 2000)
	private String justificationText;

	@Column(name = "approved_by_user_id", columnDefinition = "uuid")
	private UUID approvedByUserId;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (occurredAt == null) {
			occurredAt = Instant.now();
		}
	}
}
