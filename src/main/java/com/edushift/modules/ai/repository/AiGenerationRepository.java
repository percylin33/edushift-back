package com.edushift.modules.ai.repository;

import com.edushift.modules.ai.entity.AiGeneration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AiGeneration} (BE-7c.1).
 *
 * <p>Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * discriminator; cross-tenant lookups return empty by construction.</p>
 */
@Repository
public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> {

    Optional<AiGeneration> findByPublicUuid(UUID publicUuid);

    Page<AiGeneration> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Sweeper method (BE-7c.2 → DEBT-BE-7C-4): marks any rows that have
     * been stuck in {@code PROCESSING} for longer than the given
     * threshold as {@code FAILED} with
     * {@code error_code='SWEEPER_TIMEOUT'}.
     *
     * <p>Idempotent and safe to run from multiple nodes concurrently:
     * uses {@code SELECT ... FOR UPDATE SKIP LOCKED} so each sweeper
     * instance claims a disjoint batch of rows. Rows already in
     * {@code COMPLETED}, {@code FAILED}, or {@code CANCELLED} are
     * untouched (the WHERE clause filters on the current status).
     *
     * <p><strong>Why no @TenantId on this query:</strong> the sweeper
     * is a system job that must reconcile rows across all tenants —
     * the Hibernate tenant filter would hide rows from other tenants.
     * The query runs in a service without the filter active
     * (see {@code AiSweeper}).</p>
     *
     * <p>Returns the number of rows actually transitioned to
     * {@code FAILED} (useful for tests + monitoring).</p>
     */
    @Modifying
    @Query(value = """
            UPDATE edushift.ai_generations
            SET status = 'FAILED',
                error_code = 'SWEEPER_TIMEOUT',
                error_message = :errorMessage,
                updated_at = now()
            WHERE id IN (
                SELECT id FROM edushift.ai_generations
                WHERE status = 'PROCESSING'
                  AND updated_at < :cutoff
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int sweepStuckProcessing(@Param("cutoff") Instant cutoff,
                             @Param("errorMessage") String errorMessage);
}
