package com.edushift.infrastructure.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Token-bucket-style in-memory rate limiter (Sprint 10 / SEC-10.1,
 * extended in F6 / QA plan 2026-07-02).
 *
 * <p>Per-key, per-window. Suitable for the load EduShift sees today
 * (single-instance, &lt;100k req/min peak). For multi-instance prod, swap
 * to a Redis-backed counter — the public surface of this class is
 * stable so the interceptor does not need to change.</p>
 *
 * <h3>API</h3>
 * <ul>
 *   <li>{@link #allow(String, int, int)} — primitive; kept for callers
 *       that don't need the response shape (MercadoPago webhook,
 *       admin login).</li>
 *   <li>{@link #tryAcquire(String, int, int)} — returns a {@link Decision}
 *       carrying the {@code allowed} flag and the current
 *       {@code remaining}/{@code limit} counts so the interceptor can
 *       emit {@code X-RateLimit-*} headers.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * The outer {@link ConcurrentHashMap} protects structural updates.
 * The inner counter is held by a dedicated monitor per key so a slow
 * bucket doesn't slow down other buckets.
 */
@Component
public class SimpleRateLimiter {

	/**
	 * Per-rule key. Value: current count + window reset time + last-access nanos.
	 */
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	/**
	 * Allow one request through if the count in the current {@code windowMs}
	 * is below {@code max}. Primitive overload for callers that do not
	 * need the response shape (kept for backward compatibility with
	 * MercadoPago webhook and admin login).
	 */
	public boolean allow(String key, int max, int windowMs) {
		return tryAcquire(key, max, windowMs).allowed();
	}

	/**
	 * Try to acquire one token under the budget (capacity = {@code max},
	 * window = {@code windowMs}). Returns a {@link Decision} describing
	 * whether the call is allowed plus the current count for header emit.
	 */
	public Decision tryAcquire(String key, int max, int windowMs) {
		long now = System.currentTimeMillis();
		Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(now + windowMs));
		synchronized (bucket) {
			if (now > bucket.resetAt) {
				bucket.resetAt = now + windowMs;
				bucket.count.set(0);
			}
			int current = bucket.count.incrementAndGet();
			int remaining = Math.max(0, max - current);
			boolean allowed = current <= max;
			return new Decision(allowed, max, remaining, bucket.resetAt);
		}
	}

	/** Result of a {@link #tryAcquire}. */
	public record Decision(boolean allowed, int limit, int remaining, long resetAtMs) { }

	private static final class Bucket {
		volatile long resetAt;
		final AtomicInteger count = new AtomicInteger(0);
		Bucket(long resetAt) { this.resetAt = resetAt; }
	}
}
