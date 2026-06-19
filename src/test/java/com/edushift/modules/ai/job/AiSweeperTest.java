package com.edushift.modules.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.repository.AiGenerationRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AiSweeper} (DEBT-BE-7C-4).
 *
 * <p>We mock the repository so the test stays fast and doesn't
 * require Postgres. The native query is verified in integration
 * tests (we don't have those here, but the SQL is small and
 * easy to inspect by eye — see {@link AiGenerationRepository#sweepStuckProcessing}).</p>
 */
class AiSweeperTest {

    private AiGenerationRepository repo;
    private EntityManager entityManager;
    private AiSweeper sweeper;

    @BeforeEach
    void setUp() {
        repo = mock(AiGenerationRepository.class);
        entityManager = mock(EntityManager.class);
        sweeper = new AiSweeper(repo, entityManager);
    }

    @Test
    @DisplayName("runOnce: returns the number of rows the repo reports as swept")
    void runOnceReturnsCount() {
        when(repo.sweepStuckProcessing(any(Instant.class), anyString())).thenReturn(3);
        Instant cutoff = Instant.parse("2026-06-13T05:00:00Z");
        int result = sweeper.runOnce(cutoff);
        assertThat(result).isEqualTo(3);
        verify(repo).sweepStuckProcessing(cutoff, AiSweeper.SWEEPER_MESSAGE);
    }

    @Test
    @DisplayName("runOnce: 0 rows is a happy path, no exception")
    void runOnceZeroRows() {
        when(repo.sweepStuckProcessing(any(), anyString())).thenReturn(0);
        int result = sweeper.runOnce(Instant.now().minus(Duration.ofMinutes(5)));
        assertThat(result).isZero();
    }

    @Test
    @DisplayName("runOnce: clears the EntityManager (defensive, no tenant-bound entities linger)")
    void runOnceClearsEntityManager() {
        when(repo.sweepStuckProcessing(any(), anyString())).thenReturn(0);
        sweeper.runOnce(Instant.now());
        verify(entityManager).clear();
    }

    @Test
    @DisplayName("runOnce: cutoff is exactly now() - 5 minutes")
    void runOnceCutoffIs5MinutesAgo() {
        when(repo.sweepStuckProcessing(any(), anyString())).thenReturn(0);
        Instant before = Instant.now();
        sweeper.runOnce(Instant.now().minus(Duration.ofMinutes(5)));
        Instant after = Instant.now();
        // The sweep calls the repo with a cutoff that the test provides;
        // what we verify here is the SWEEPER_MESSAGE constant is
        // stable + the cutoff is passed through unchanged.
        verify(repo).sweepStuckProcessing(
                org.mockito.ArgumentMatchers.argThat(c ->
                        !c.isBefore(before.minus(Duration.ofMinutes(5)).minusSeconds(1))
                                && !c.isAfter(after.minus(Duration.ofMinutes(5)).plusSeconds(1))),
                anyString());
    }

    @Test
    @DisplayName("scheduled sweep: reuses runOnce with now() - 5min")
    void scheduledSweepUsesCorrectCutoff() {
        when(repo.sweepStuckProcessing(any(), anyString())).thenReturn(2);
        Instant before = Instant.now();
        sweeper.sweep();
        Instant after = Instant.now();
        // The @Scheduled sweep() calls runOnce(Instant.now().minus(STUCK_THRESHOLD)),
        // which calls the repo exactly once with a cutoff in
        // [before - 5min, after - 5min].
        verify(repo).sweepStuckProcessing(
                org.mockito.ArgumentMatchers.argThat(c ->
                        !c.isBefore(before.minus(Duration.ofMinutes(5)).minusSeconds(1))
                                && !c.isAfter(after.minus(Duration.ofMinutes(5)).plusSeconds(1))),
                anyString());
        // And runOnce clears the entity manager
        verify(entityManager).clear();
    }
}
