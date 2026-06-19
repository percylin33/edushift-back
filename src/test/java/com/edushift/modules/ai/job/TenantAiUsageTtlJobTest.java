package com.edushift.modules.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TenantAiUsageTtlJob} (DEBT-BE-7C-1).
 *
 * <p>We mock the repository so the test stays fast and doesn't
 * require Postgres. The native query is verified in integration
 * tests (we don't have those here, but the SQL is small and easy
 * to inspect by eye — see {@link TenantAiUsageRepository#deleteOlderThan}).</p>
 */
class TenantAiUsageTtlJobTest {

    private TenantAiUsageRepository repo;
    private EntityManager entityManager;
    private TenantAiUsageTtlJob job;

    @BeforeEach
    void setUp() {
        repo = mock(TenantAiUsageRepository.class);
        entityManager = mock(EntityManager.class);
        job = new TenantAiUsageTtlJob(repo, entityManager);
    }

    @Test
    @DisplayName("runOnce: returns the number of rows the repo reports as deleted")
    void runOnceReturnsCount() {
        when(repo.deleteOlderThan(any(Instant.class))).thenReturn(7);
        Instant cutoff = Instant.parse("2026-03-15T00:00:00Z");
        int result = job.runOnce(cutoff);
        assertThat(result).isEqualTo(7);
        verify(repo).deleteOlderThan(cutoff);
    }

    @Test
    @DisplayName("runOnce: 0 rows is a happy path, no exception")
    void runOnceZeroRows() {
        when(repo.deleteOlderThan(any(Instant.class))).thenReturn(0);
        int result = job.runOnce(Instant.now().minus(Duration.ofDays(90)));
        assertThat(result).isZero();
    }

    @Test
    @DisplayName("runOnce: clears the EntityManager (defensive, no tenant-bound entities linger)")
    void runOnceClearsEntityManager() {
        when(repo.deleteOlderThan(any())).thenReturn(0);
        job.runOnce(Instant.now());
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("scheduled run: reuses runOnce with now() - 90 days")
    void scheduledRunUsesCorrectCutoff() {
        when(repo.deleteOlderThan(any(Instant.class))).thenReturn(42);
        Instant before = Instant.now();
        job.runScheduled();
        Instant after = Instant.now();
        // The @Scheduled runScheduled() calls runOnce(Instant.now().minus(RETENTION=90d)),
        // which calls the repo exactly once with a cutoff in
        // [before - 90d, after - 90d].
        verify(repo).deleteOlderThan(
                org.mockito.ArgumentMatchers.argThat(c ->
                        !c.isBefore(before.minus(Duration.ofDays(90)).minusSeconds(1))
                                && !c.isAfter(after.minus(Duration.ofDays(90)).plusSeconds(1))));
        // And runOnce clears the entity manager
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("runOnce: cutoff is passed through unchanged (no magic 90-day window)")
    void runOnceCutoffIsPassthrough() {
        when(repo.deleteOlderThan(any(Instant.class))).thenReturn(0);
        Instant explicitCutoff = Instant.parse("2025-01-01T00:00:00Z");
        job.runOnce(explicitCutoff);
        verify(repo).deleteOlderThan(explicitCutoff);
    }

    @Test
    @DisplayName("default retention constant: 90 days (sanity, do not change silently)")
    void defaultRetentionIs90Days() {
        // If you change this, also update the doc + tech-debt.md.
        // It's pinned here so the assertion fires if someone "improves"
        // it to 30 or 365 without thinking.
        assertThat(TenantAiUsageTtlJob.RETENTION).isEqualTo(Duration.ofDays(90));
    }
}
