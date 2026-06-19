package com.edushift.modules.ai.service;

import com.edushift.modules.ai.exception.AiRateLimitedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Per-user rate limit for AI generations (Sprint 8 / SEC-8.1).
 *
 * <p>Enforces two caps:</p>
 * <ul>
 *   <li><b>Per hour</b> — default 20 generations / user / hour.</li>
 *   <li><b>Per day</b>  — default 100 generations / user / day.</li>
 * </ul>
 *
 * <h3>Why in-memory (not Redis)</h3>
 * - The check is on the hot path of every AI call. Latency
 *   matters; one Redis round-trip per call adds 1-3ms and is a
 *   point of failure (Redis down → all AI blocked).
 * - Horizontal scaling: in a multi-instance setup the limit is
 *   per-instance, not global. This is acceptable for v1 (the cap
 *   is generous); the per-tenant quota (in
 *   {@code AiQuotaService}) is the global cap and stays in
 *   PostgreSQL.
 * - If we ever need global per-user limits, the swap to Redis is
 *   trivial (same {@code check(userId)} signature).
 *
 * <h3>Memory bound</h3>
 * The {@code buckets} map is keyed by userId. Each entry is small
 * (two AtomicIntegers + two Instants). With 10k users and 100k
 * generations/day, the footprint is &lt; 10 MB. The map is never
 * trimmed in v1 (acceptable for the cap; a {@code @Scheduled}
 * eviction can be added in SEC-8.x if needed).
 */
@Service
public class RateLimitService {

    @Value("${app.ai.rate-limit.per-hour:20}")
    private int perHour;

    @Value("${app.ai.rate-limit.per-day:100}")
    private int perDay;

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Check the per-user rate limit. Throws
     * {@link AiRateLimitedException} if the user has exceeded
     * either cap. Otherwise increments the counters.
     */
    public void checkAndIncrement(UUID userId) {
        Bucket b = buckets.computeIfAbsent(userId, k -> new Bucket());
        b.rollIfStale();
        if (b.hour.get() >= perHour) {
            throw new AiRateLimitedException(
                    "Has alcanzado el limite de " + perHour
                    + " generaciones de IA por hora. Intenta en unos minutos.");
        }
        if (b.day.get() >= perDay) {
            throw new AiRateLimitedException(
                    "Has alcanzado el limite de " + perDay
                    + " generaciones de IA por dia. Intenta manana.");
        }
        b.hour.incrementAndGet();
        b.day.incrementAndGet();
    }

    /** Read-only access for tests and the future /ai/usage/limits endpoint. */
    public int getPerHour() { return perHour; }
    public int getPerDay()  { return perDay; }

    /** Internal sliding-window bucket. */
    private static final class Bucket {
        final AtomicInteger hour = new AtomicInteger(0);
        final AtomicInteger day  = new AtomicInteger(0);
        volatile Instant hourReset = Instant.now().plus(Duration.ofHours(1));
        volatile Instant dayReset  = Instant.now().plus(Duration.ofDays(1));

        void rollIfStale() {
            Instant now = Instant.now();
            if (now.isAfter(hourReset)) {
                hour.set(0);
                hourReset = now.plus(Duration.ofHours(1));
            }
            if (now.isAfter(dayReset)) {
                day.set(0);
                dayReset = now.plus(Duration.ofDays(1));
            }
        }
    }
}
