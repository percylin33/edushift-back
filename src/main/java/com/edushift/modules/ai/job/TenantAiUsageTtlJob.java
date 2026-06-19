package com.edushift.modules.ai.job;

import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import com.edushift.shared.constants.LoggerNames;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TTL job that hard-deletes {@code tenant_ai_usage} rows older than
 * the configured retention window (default: 90 days).
 *
 * <h3>Why this exists (DEBT-BE-7C-1)</h3>
 * The AI module writes one row per {@code (tenant, day)} via the
 * quota counter UPSERT. Over a year, with 1000 active tenants each
 * making ~10 calls/day, that's 3.65M rows × ~120 bytes ≈ 440 MB. Not
 * catastrophic on its own, but unbounded growth in a multi-tenant
 * SaaS is technical debt: at 10K tenants × 100 calls/day, it's 30 GB
 * of counters that no one ever reads beyond the last 30-60 days.
 *
 * <h3>What it does</h3>
 * Runs once a week (Sunday 03:00 server local time, configurable)
 * and deletes all rows whose {@code created_at} is older than the
 * retention window. Hard delete (not soft) because the row is a
 * derived counter — the value is rolled into today's row, and
 * losing the row loses nothing we don't already have aggregated
 * upstream.
 *
 * <h3>Why Sunday 03:00</h3>
 * Off-peak, low-traffic slot for most EduShift tenants. Configurable
 * via {@code app.ai.ttl.cron} (Spring cron format, 6 fields).
 *
 * <h3>Why no Hibernate tenant filter</h3>
 * Same as {@link AiSweeper}: this is a system job, not a per-tenant
 * request. It must age out rows across all tenants. We use a native
 * query and clear the entity manager defensively. The job only
 * deletes rows, never reads or mutates per-tenant data.
 *
 * <h3>Failure modes</h3>
 * <ul>
 *   <li>DB unreachable → the {@code @Scheduled} invocation throws,
 *       Spring logs it, and next tick tries again. We do NOT catch
 *       and swallow (see AiSweeper rationale).</li>
 *   <li>No rows to delete → 0 returned, log DEBUG. Cheap and normal
 *       in the days right after deploy.</li>
 *   <li>A tenant with a runaway counter (10K rows in a day) is not
 *       blocked by the job: it processes all rows in a single SQL
 *       DELETE; the {@code WHERE created_at < :cutoff} ensures it
 *       only touches expired rows.</li>
 * </ul>
 */
@Component
public class TenantAiUsageTtlJob {

    private static final Logger log = LoggerFactory.getLogger(LoggerNames.AI);

    /** Default retention window. Overridable via config. */
    static final Duration RETENTION = Duration.ofDays(90);

    private final TenantAiUsageRepository repo;
    private final EntityManager entityManager;

    public TenantAiUsageTtlJob(TenantAiUsageRepository repo, EntityManager entityManager) {
        this.repo = repo;
        this.entityManager = entityManager;
    }

    /**
     * Scheduled entry point. Default: every Sunday at 03:00 server
     * time. Cron format is Spring's 6-field (sec min hour day-of-month
     * month day-of-week).
     *
     * <p>Defaults can be overridden via {@code app.ai.ttl.cron}
     * and {@code app.ai.ttl.initial-delay-ms}.</p>
     */
    @Scheduled(cron = "${app.ai.ttl.cron:0 0 3 * * SUN}",
               zone = "${app.ai.ttl.zone:America/Mexico_City}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runScheduled() {
        runOnce(Instant.now().minus(RETENTION));
    }

    /**
     * Test/operator-friendly entry point: run a TTL pass with a
     * caller-supplied cutoff. Returns the number of rows deleted.
     * Same semantics as {@link #runScheduled()} but no scheduling
     * and no default retention — caller decides.
     */
    public int runOnce(Instant cutoff) {
        entityManager.clear();
        int deleted = repo.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("AI usage TTL: deleted {} tenant_ai_usage row(s) older than {}",
                    deleted, cutoff);
        } else {
            log.debug("AI usage TTL: 0 rows to delete (cutoff={})", cutoff);
        }
        return deleted;
    }
}
