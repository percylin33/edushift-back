package com.edushift.infrastructure.ratelimit;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate-limit configuration (QA plan 2026-07-02 / 10-rate-limit-spec-y-status.md).
 *
 * <p>Bound to {@code edushift.ratelimit.*}. Use {@link #enabled} to switch
 * the whole subsystem off in tests / local dev (e.g. during a session
 * with a noisy CI rerun). The interceptor is registered for ALL paths
 * declared in {@link #rules}; an empty list + {@code enabled=false} is
 * the no-op default.</p>
 *
 * <h3>Rule structure</h3>
 * <ul>
 *   <li>{@link Rule#getPath()} — Ant-style path pattern. Required.</li>
 *   <li>{@link Rule#getScope()} — key dimension: {@code IP}, {@code USER},
 *       or {@code TENANT}. See {@link Scope} javadoc.</li>
 *   <li>{@link Rule#getCapacity()} — max requests inside the window.</li>
 *   <li>{@link Rule#getRefillSeconds()} — window length in seconds.
 *       Bucket4j refills {@code capacity} tokens every {@code refillSeconds}.</li>
 *   <li>{@link Rule#getBurst()} — optional override for the burst capacity
 *       (defaults to {@link Rule#getCapacity()}).</li>
 * </ul>
 *
 * <p>{@code refillSeconds} is the sliding-window equivalent of
 * "X per Y minutes" (e.g. 10 per 1 minute → capacity=10, refill=60).</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "edushift.ratelimit")
public class RateLimitProperties {

	/** Master switch — when false, the interceptor allow-lists every request. */
	private boolean enabled = true;

	/**
	 * List of per-endpoint rules. Order does not matter; the first rule
	 * whose {@code path} matches the request URI is applied.
	 */
	private List<Rule> rules = List.of();

	/** Optional: show rate-limit headers on every response for visibility. */
	private boolean emitHeaders = true;

	@Getter
	@Setter
	public static class Rule {
		/** Ant path pattern, e.g. {@code /v1/auth/login}, {@code /v1/users/invitations/**}. */
		private String path;

		/** {@code IP}, {@code USER}, or {@code TENANT}. */
		private Scope scope = Scope.IP;

		/** Max requests inside the window (capacity). */
		private int capacity;

		/** Window length in seconds. */
		private int refillSeconds;

		/** Optional burst capacity (defaults to {@link #capacity}). */
		private Integer burst;

		/** Free-text comment for ops — surfaces in /actuator and metrics labels. */
		private String description;
	}

	public enum Scope {
		/**
		 * Per-IP counting. Uses the IP resolved from {@code X-Forwarded-For}
		 * (first hop) when present, falling back to the request's remote address.
		 * Used for unauthenticated endpoints (login, register, forgot).
		 */
		IP,

		/**
		 * Per-authenticated-user counting. Uses the {@code publicUuid} from the
		 * Spring Security context (set by {@code JwtAuthenticationFilter}).
		 * Used for endpoints where the actor is known.
		 */
		USER,

		/**
		 * Per-tenant counting. Uses the resolved {@code tenantId} from the
		 * security context. Used for cost-bearing endpoints (AI, file uploads).
		 */
		TENANT
	}
}
