package com.edushift.modules.auth.exception;

import com.edushift.shared.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a user is temporarily locked due to too many failed login
 * attempts (DEBT-AUTH-7). Carries the {@code lockedUntil} timestamp so
 * the controller layer can emit a {@code Retry-After} header.
 *
 * <p>Maps to HTTP 429 Too Many Requests — the standard for "you've hit
 * a rate limit on this resource". The body shape follows the standard
 * {@link com.edushift.shared.api.ApiErrorResponse}.
 */
@Getter
public class UserLockedException extends ApiException {

	private final java.time.Instant lockedUntil;

	private final long retryAfterSeconds;

	public UserLockedException(java.time.Instant lockedUntil) {
		super(HttpStatus.TOO_MANY_REQUESTS,
				"USER_TEMPORARILY_LOCKED",
				"Account is temporarily locked due to too many failed login attempts. "
						+ "Try again at " + lockedUntil + ".");
		this.lockedUntil = lockedUntil;
		this.retryAfterSeconds = Math.max(1,
				lockedUntil.getEpochSecond() - java.time.Instant.now().getEpochSecond());
	}
}
