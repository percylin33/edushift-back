package com.edushift.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-scope rate limiter for sensitive endpoints (QA plan 2026-07-02 /
 * 10-rate-limit-spec-y-status.md / F6).
 *
 * <p>Closes <strong>DEBT-TEN-6</strong> (tenant register) and adds coverage
 * for auth endpoints, invitation flows, and AI cost-bearing endpoints.
 * Each rule ({@link RateLimitProperties.Rule}) defines:</p>
 * <ul>
 *   <li>Ant path pattern (e.g. {@code /v1/auth/**})</li>
 *   <li>Scope — {@code IP}, {@code USER}, or {@code TENANT}</li>
 *   <li>Capacity + refill period (token-bucket semantics)</li>
 * </ul>
 *
 * <p>The first matching rule wins; a request with no matching rule is
 * allowed through unchanged.</p>
 *
 * <h3>Fail-open / fail-closed</h3>
 * If the storage of the underlying limiter ever fails, the interceptor
 * fails OPEN (allows the request and logs at WARN). For a SaaS login
 * surface, blocking legitimate traffic is worse than letting through
 * a few extra requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

	private final ObjectMapper objectMapper;
	private final RateLimitProperties properties;
	private final SimpleRateLimiter limiter;

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	/**
	 * Per-IP counters (used when the matching rule has scope = IP).
	 * Keyed by rule-name + client IP.
	 */
	private final ConcurrentHashMap<String, WindowCounter> ipCounters = new ConcurrentHashMap<>();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		if (!properties.isEnabled()) {
			return true;
		}

		String requestPath = request.getRequestURI();
		RateLimitProperties.Rule matched = null;
		for (RateLimitProperties.Rule rule : properties.getRules()) {
			if (rule.getPath() == null) continue;
			if (pathMatcher.match(rule.getPath(), requestPath)) {
				matched = rule;
				break;
			}
		}
		if (matched == null) {
			return true;
		}

		String scopeKey = resolveKey(request, matched);
		String key = matched.getDescription() == null
				? matched.getPath() + ":" + scopeKey
				: matched.getDescription() + ":" + scopeKey;

		int capacity = matched.getCapacity();
		int burst = matched.getBurst() == null ? capacity : matched.getBurst();
		int windowMs = Math.max(1, matched.getRefillSeconds()) * 1000;

		// We use the SimpleRateLimiter for the actual limit check (cheap +
		// thread-safe) and emit the response headers from the decision.
		SimpleRateLimiter.Decision decision = limiter.tryAcquire(
				"rl:" + key, Math.max(capacity, burst), windowMs);

		if (properties.isEmitHeaders()) {
			response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
			response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
			long secs = Math.max(0, (decision.resetAtMs() - System.currentTimeMillis()) / 1000);
			response.setHeader("X-RateLimit-Reset", String.valueOf(secs));
		}

		if (!decision.allowed()) {
			log.warn("[ratelimit] blocked key={} path={} limit={} windowMs={}",
					key, requestPath, decision.limit(), windowMs);
			writeTooManyRequests(response, decision);
			return false;
		}
		return true;
	}

	/**
	 * Resolves the discriminator value for the matching rule's scope.
	 *
	 * <ul>
	 *   <li>{@code IP} — client IP (X-Forwarded-For aware).</li>
	 *   <li>{@code USER} — publicUuid of the authenticated principal.</li>
	 *   <li>{@code TENANT} — tenantId of the authenticated principal.</li>
	 * </ul>
	 */
	private static String resolveKey(HttpServletRequest request, RateLimitProperties.Rule rule) {
		return switch (rule.getScope()) {
			case IP -> resolveClientIp(request);
			case USER -> resolveUserKey(request);
			case TENANT -> resolveTenantKey(request);
		};
	}

	private static String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			int comma = xff.indexOf(',');
			return (comma > 0 ? xff.substring(0, comma) : xff).trim();
		}
		String real = request.getHeader("X-Real-IP");
		if (real != null && !real.isBlank()) {
			return real.trim();
		}
		String remote = request.getRemoteAddr();
		return remote == null ? "unknown" : remote;
	}

	private static String resolveUserKey(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getPrincipal() == null) {
			// Auth happens AFTER the interceptor in the chain. Fall back to IP.
			return "anon:" + resolveClientIp(request);
		}
		return String.valueOf(auth.getPrincipal());
	}

	private static String resolveTenantKey(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getPrincipal() == null) {
			return "anon:" + resolveClientIp(request);
		}
		Object principal = auth.getPrincipal();
		if (principal instanceof com.edushift.infrastructure.security.AuthenticatedPrincipal p) {
			UUID tid = p.getTenantId();
			return tid == null ? "no-tenant" : tid.toString();
		}
		// Last resort: degrade to IP — better than crashing.
		return "unknown:" + resolveClientIp(request);
	}

	private void writeTooManyRequests(HttpServletResponse response,
									 SimpleRateLimiter.Decision decision) throws java.io.IOException {
		long retryAfterSec = Math.max(1, (decision.resetAtMs() - System.currentTimeMillis()) / 1000);
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader("Retry-After", String.valueOf(retryAfterSec));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String message = "Too many requests. Limit: " + decision.limit() + ".";
		com.edushift.shared.api.ApiResponse<Object> body =
				com.edushift.shared.api.ApiResponse.error("RATE_LIMITED", message);
		objectMapper.writeValue(response.getWriter(), body);
	}

	/** WindowCounter — typed alias for older API; preserved for tests. */
	private static final class WindowCounter {
		@SuppressWarnings("unused")
		final AtomicInteger count = new AtomicInteger(0);
		@SuppressWarnings("unused")
		volatile long lastAccessNanos;
	}
}
