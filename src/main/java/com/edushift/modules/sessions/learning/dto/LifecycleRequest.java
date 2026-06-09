package com.edushift.modules.sessions.learning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Common payload for the three lifecycle endpoints
 * ({@code /start}, {@code /complete}, {@code /cancel}) of a
 * {@code LearningSession} (Sprint 5A / BE-5A.4).
 *
 * <p>Carries the entity {@code version} the FE loaded with so the
 * server can reject stale lifecycle clicks (a second admin clicking
 * "Start" after the first one already started). Mismatch causes a
 * 409 {@code SESSION_VERSION_CONFLICT}.</p>
 *
 * <p>For {@code /cancel} an optional {@code reason} can be attached
 * for the audit trail (free text, max 500 chars). Ignored by the other
 * two endpoints.</p>
 */
public record LifecycleRequest(

		@NotNull(message = "version is required")
		@PositiveOrZero(message = "version must be >= 0")
		Long version,

		String reason
) {
}
