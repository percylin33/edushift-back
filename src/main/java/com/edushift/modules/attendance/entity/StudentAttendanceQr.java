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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * <strong>StudentAttendanceQr</strong> — issued / revoked lifecycle of
 * the QR JWT printed on a student's credential. Sprint 6 / BE-6.1.
 *
 * <h3>Identity</h3>
 * No {@code publicUuid}: this entity is not exposed as a REST resource
 * with its own URL. The client receives the JWT rendered as an SVG/PNG
 * image (via {@code GET /students/{uuid}/attendance-qr}) and the only
 * mutation is rotation by an admin
 * ({@code POST /students/{uuid}/attendance-qr/rotate}). Same pattern as
 * {@code evaluation_rubric} (V28).
 *
 * <h3>Token storage (security)</h3>
 * We persist {@link #tokenHash} (SHA-256 hex of the raw JWT), never the
 * JWT itself. A DB leak therefore does NOT enable QR forgery. Same
 * pattern as {@code refresh_tokens} in {@code auth.md}.
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>At most ONE active row per student (non-deleted, non-revoked).
 *       Enforced by partial unique index
 *       {@code uk_qr_student_active}.</li>
 *   <li>{@link #tokenHash} is globally unique among non-deleted rows
 *       (partial unique index {@code uk_qr_token_hash}).</li>
 *   <li>{@link #revokedAt} and {@link #revokedReason} travel together
 *       (DB CHECK {@code chk_qr_revoked_consistent}).</li>
 *   <li>Hash format is locked to {@code [0-9a-f]{64}} via
 *       {@code chk_qr_token_hash_format}.</li>
 * </ul>
 */
@Entity
@Table(
		name = "student_attendance_qr",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_qr_token_hash",
						columnNames = "token_hash")
		},
		indexes = {
				@Index(name = "idx_qr_tenant_student",
						columnList = "tenant_id, student_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"issuedAt", "revokedAt", "revokedReason"})
@SQLDelete(sql = "UPDATE edushift.student_attendance_qr "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class StudentAttendanceQr extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_qr_student"))
	private Student student;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "revoked_reason", length = 32)
	private QrRevokedReason revokedReason;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (issuedAt == null) {
			issuedAt = Instant.now();
		}
	}

	/** @return {@code true} when the row is the currently-usable QR. */
	public boolean isActive() {
		return revokedAt == null;
	}
}
