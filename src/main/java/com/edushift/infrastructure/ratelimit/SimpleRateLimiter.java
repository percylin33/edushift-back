package com.edushift.infrastructure.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Token-bucket-style in-memory rate limiter (Sprint 10 / SEC-10.1).
 *
 * <p>Per-key, per-window. Suitable for the MP webhook (~5 req/min
 * expected). For multi-instance prod, swap to Redis-backed counters.</p>
 *
 * <p>Use {@link #allow(String, int, int)}: returns true if the
 * request is allowed under the budget.</p>
 */
@Component
public class SimpleRateLimiter {

    /** Key: rate-limit key. Value: current count + window reset time. */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Allow one request through if the count in the current
     * {@code windowMs} is below {@code max}.
     */
    public boolean allow(String key, int max, int windowMs) {
        long now = System.currentTimeMillis();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(now + windowMs));
        synchronized (b) {
            if (now > b.resetAt) {
                b.resetAt = now + windowMs;
                b.count.set(0);
            }
            int c = b.count.incrementAndGet();
            return c <= max;
        }
    }

    private static final class Bucket {
        volatile long resetAt;
        final AtomicInteger count = new AtomicInteger(0);
        Bucket(long resetAt) { this.resetAt = resetAt; }
    }
}
