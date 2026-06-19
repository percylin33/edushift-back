package com.edushift.modules.ai.job;

import com.edushift.modules.ai.repository.AiGenerationRepository;
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
 * Periodic sweeper that reconciles {@code ai_generations} rows that
 * have been stuck in {@code PROCESSING} for longer than the configured
 * threshold (default: 5 minutes).
 *
 * <h3>Why this exists (DEBT-BE-7C-4)</h3>
 * The async job runner (BE-7c.2) marks a row {@code PROCESSING} on
 * the worker thread, then runs the LLM call, then updates it to
 * {@code COMPLETED} or {@code FAILED}. If the JVM dies between the
 * LLM call and the {@code markCompleted} (OOM kill, container eviction,
 * K8s node drain), the row stays {@code PROCESSING} forever —
 * polluting the future audit dashboard and breaking client polls.
 *
 * <h3>What it does</h3>
 * Every 5 minutes (configurable via {@code app.ai.sweeper.interval}),
 * this job runs a single SQL update that flips any {@code PROCESSING}
 * row whose {@code updated_at} is older than the cutoff to
 * {@code FAILED} with {@code error_code='SWEEPER_TIMEOUT'}. The
 * {@code FOR UPDATE SKIP LOCKED} clause makes the operation safe to
 * run from multiple nodes simultaneously (each node claims a disjoint
 * batch of rows; no double-work, no deadlock).
 *
 * <h3>Why no Hibernate tenant filter</h3>
 * This is a system job: it must reconcile rows across <em>all</em>
 * tenants, not just the caller's. We bypass the Hibernate
 * {@code @TenantId} filter by using a native query and binding the
 * entity manager to a "system" persistence context. The sweeper does
 * not read or write per-tenant data; it only transitions row status
 * fields.
 *
 * <h3>Why no ShedLock</h3>
 * {@code FOR UPDATE SKIP LOCKED} (Postgres 9.5+) achieves the same
 * thing — single-node coordination — without an extra dependency on
 * ShedLock + a DB lock table. If we ever migrate off Postgres, we'll
 * need to revisit.
 *
 * <h3>Failure modes</h3>
 * <ul>
 *   <li>DB unreachable → the {@code @Scheduled} invocation throws,
 *       Spring logs it, and the next tick tries again. We do NOT
 *       catch and swallow: silent failures are worse than noisy ones
 *       here, because a stuck sweeper = stuck rows = no recovery.</li>
 *   <li>No rows to sweep → the UPDATE matches 0 rows, returns 0,
 *       INFO log line. Cheap and normal.</li>
 * </ul>
 */
@Component
public class AiSweeper {

    private static final Logger log = LoggerFactory.getLogger(LoggerNames.AI);

    /** Hard-coded message. Stable for tests and dashboards. */
    static final String SWEEPER_MESSAGE =
            "Row stuck in PROCESSING for over 5 minutes — sweeper reconciling "
                    + "after likely JVM crash or worker thread leak. The original LLM call "
                    + "may have succeeded server-side; the result is lost.";

    /** Threshold: rows older than this in PROCESSING get swept. */
    static final Duration STUCK_THRESHOLD = Duration.ofMinutes(5);

    private final AiGenerationRepository repo;
    private final EntityManager entityManager;

    public AiSweeper(AiGenerationRepository repo, EntityManager entityManager) {
        this.repo = repo;
        this.entityManager = entityManager;
    }

    /**
     * Periodic entry point. Default cron is every 5 minutes
     * (configurable via {@code app.ai.sweeper.interval} in ms).
     * The {@code fixedDelayString} is the pause between the END of
     * one run and the START of the next, so a slow DB query won't
     * pile up overlapping invocations.
     */
    @Scheduled(fixedDelayString = "${app.ai.sweeper.interval-ms:300000}",
               initialDelayString = "${app.ai.sweeper.initial-delay-ms:60000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sweep() {
        runOnce(Instant.now().minus(STUCK_THRESHOLD));
    }

    /**
     * Test/operator-friendly entry point: run a sweep pass with a
     * caller-supplied cutoff. Returns the number of rows
     * transitioned. Same semantics as {@link #sweep()} but no
     * scheduling.
     */
    public int runOnce(Instant cutoff) {
        // Detach any tenant-bound entities to be safe (this bean
        // doesn't read entities directly, but defensive measure in
        // case a future refactor adds that).
        entityManager.clear();
        int swept = repo.sweepStuckProcessing(cutoff, SWEEPER_MESSAGE);
        if (swept > 0) {
            log.warn("AI sweeper reconciled {} stuck PROCESSING row(s) older than {}",
                    swept, cutoff);
        } else {
            log.debug("AI sweeper found 0 stuck PROCESSING rows (cutoff={})", cutoff);
        }
        return swept;
    }
}
