package com.edushift.modules.auth.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
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
 * Durable counter for failed login attempts.
 *
 * <p>Closes <strong>DEBT-AUTH-7</strong> alongside the in-memory Redis cache.
 * One row per (tenant, email, attempt-window-start) keeps the table bounded
 * (at most one ACTIVE row per email per 15-minute window).
 *
 * <h3>Why also persisted (not just Redis)</h3>
 * A Redis flush (or restart in a single-node setup) must not silently
 * re-open a locked account. This table is the source of truth at the SQL
 * boundary. {@link com.edushift.modules.auth.service.LoginAttemptService}
 * reads from Redis first (fast path) and falls back to this table.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   recordFailure()  →  INSERT or UPDATE row (status=ACTIVE, ++attempt_count)
 *   threshold (5)    →  UPDATE row  status=LOCKED, locked_until=now+15m
 *   recordSuccess()  →  UPDATE row  status=CLEARED, attempt_count=0
 *   24h old          →  status=EXPIRED (set by cleanup job DEBT-AUTH-7-CLEANUP)
 * </pre>
 */
@Entity
@Table(
		name = "failed_login_attempts",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_failed_login_attempts_window",
						columnNames = {"tenant_id", "email", "first_attempt_at"}
				)
		},
		indexes = {
				@Index(
						name = "idx_failed_login_attempts_lookup",
						columnList = "tenant_id, email, last_attempt_at"
				)
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"tenantSlug", "email", "attemptCount", "status", "lockedUntil"})
@SQLDelete(sql = "UPDATE edushift.failed_login_attempts "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class FailedLoginAttempt extends TenantAwareEntity {

	public static final int MAX_ATTEMPTS_BEFORE_LOCK = 5;

	public static final java.time.Duration LOCK_DURATION = java.time.Duration.ofMinutes(15);

	public static final java.time.Duration ATTEMPT_WINDOW = java.time.Duration.ofMinutes(15);

	public enum Status { ACTIVE, LOCKED, EXPIRED, CLEARED }

	@Column(name = "tenant_slug", nullable = false, length = 64, updatable = false)
	private String tenantSlug;

	@Column(name = "email", nullable = false, length = 254, updatable = false)
	private String email;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "first_attempt_at", nullable = false, updatable = false)
	private Instant firstAttemptAt;

	@Column(name = "last_attempt_at", nullable = false)
	private Instant lastAttemptAt;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private Status status = Status.ACTIVE;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/** Reset the counter (called on successful authentication). */
	public void clear() {
		this.status = Status.CLEARED;
		this.attemptCount = 0;
		this.lastAttemptAt = Instant.now();
		this.lockedUntil = null;
	}

	/** Increment attempt count and update timestamp. */
	public void increment() {
		this.attemptCount++;
		this.lastAttemptAt = Instant.now();
	}

	/** Apply lockout: 5+ failures, set status and locked_until. */
	public void applyLockout() {
		this.status = Status.LOCKED;
		this.lockedUntil = Instant.now().plus(LOCK_DURATION);
	}

	public boolean isLocked() {
		return status == Status.LOCKED && lockedUntil != null && lockedUntil.isAfter(Instant.now());
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
