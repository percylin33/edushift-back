package com.edushift.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-IP rate limiter for sensitive public endpoints.
 *
 * <p>Closes <strong>DEBT-TEN-6</strong>: limits {@code POST /v1/tenants/register}
 * (and any other route registered for this interceptor) to {@value #MAX_REQUESTS_PER_WINDOW}
 * requests per {@value #WINDOW_MINUTES} minutes per source IP. Enforced
 * via a simple in-memory sliding window. Returns HTTP 429 Too Many Requests
 * with a {@code RATE_LIMITED} error code on breach.
 *
 * <h3>Why a sliding window instead of token bucket</h3>
 * A sliding window of fixed size is the simplest correct implementation for
 * "no more than N requests per X minutes per IP" — the requirement spelled
 * out in the debt. The trade-off is memory: we keep at most one
 * {@link WindowCounter} per IP, capped at {@value #CACHE_MAX_ENTRIES} entries
 * with {@link WindowCounter#lastAccessNanos} used to evict idle entries.
 *
 * <h3>Future work (post-v1.0)</h3>
 * The store is local-memory only and does <em>not</em> survive a restart or
 * share state across instances. When the platform goes multi-instance this
 * should be migrated to a distributed bucket (Bucket4j with Redis backend,
 * or Resilience4j RateLimiter with a Redis registry). The interceptor's
 * public surface ({@link #preHandle}) will not change.
 *
 * <h3>How IPs are resolved</h3>
 * Honors the {@code X-Forwarded-For} header (first hop) when present,
 * falling back to {@code request.getRemoteAddr()}. This lets the limiter
 * work behind an Nginx reverse proxy — the proxy must strip any
 * client-supplied {@code X-Forwarded-For} (see {@code docs/devops/nginx.md}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

	/** Max requests per window per IP. Mirrors the DEBT-TEN-6 spec. */
	public static final int MAX_REQUESTS_PER_WINDOW = 5;

	/** Window length in minutes. */
	public static final int WINDOW_MINUTES = 60;

	/** Max distinct IPs tracked simultaneously. Older entries are evicted on overflow. */
	private static final int CACHE_MAX_ENTRIES = 50_000;

	/** Idle eviction: a counter older than this is reset on the next request. */
	private static final long IDLE_NANOS = Duration.ofMinutes(WINDOW_MINUTES).toNanos();

	private final ObjectMapper objectMapper;

	/** Keyed by IP; value is a counter window. */
	private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		String ip = resolveClientIp(request);
		long now = System.nanoTime();

		WindowCounter counter = counters.compute(ip, (k, existing) -> {
			if (existing == null) {
				if (counters.size() >= CACHE_MAX_ENTRIES) {
					evictOldest();
				}
				return new WindowCounter(now);
			}
			// Reset if the previous window has fully elapsed (idle longer than the window)
			if (now - existing.lastAccessNanos > IDLE_NANOS) {
				return new WindowCounter(now);
			}
			existing.lastAccessNanos = now;
			return existing;
		});

		int count = counter.count.incrementAndGet();
		if (count > MAX_REQUESTS_PER_WINDOW) {
			log.warn("[ratelimit] blocked ip={} path={} count={}/{} per {}m",
					ip, request.getRequestURI(), count, MAX_REQUESTS_PER_WINDOW, WINDOW_MINUTES);
			writeTooManyRequests(response, count);
			return false;
		}
		return true;
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
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}

	private void evictOldest() {
		// Best-effort: drop the entry with the smallest lastAccessNanos.
		// Acceptable because eviction is rare (only on the CACHE_MAX_ENTRIES
		// boundary) and a momentary miss just resets that IP's window.
		String victim = null;
		long oldest = Long.MAX_VALUE;
		for (var e : counters.entrySet()) {
			long ts = e.getValue().lastAccessNanos;
			if (ts < oldest) {
				oldest = ts;
				victim = e.getKey();
			}
		}
		if (victim != null) {
			counters.remove(victim, counters.get(victim));
		}
	}

	private void writeTooManyRequests(HttpServletResponse response, int count) throws java.io.IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String message = "Too many requests. Limit: " + MAX_REQUESTS_PER_WINDOW
				+ " per " + WINDOW_MINUTES + " minutes per IP.";
		com.edushift.shared.api.ApiResponse<Object> body =
				com.edushift.shared.api.ApiResponse.error("RATE_LIMITED", message);
		objectMapper.writeValue(response.getWriter(), body);
	}

	/**
	 * Single counter window for one IP. Holds the count of requests seen in
	 * the current window plus the timestamp of the last access (used for
	 * idle-eviction).
	 */
	private static final class WindowCounter {
		final AtomicInteger count = new AtomicInteger(0);
		volatile long lastAccessNanos;

		WindowCounter(long now) {
			this.lastAccessNanos = now;
		}
	}
}
