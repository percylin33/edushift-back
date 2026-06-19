package com.edushift.modules.notifications.repository;

import com.edushift.modules.notifications.entity.EmailOutbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link EmailOutbox} (Sprint 9 / BE-9.1, ADR-9.1).
 *
 * <p>The {@code EmailOutboxProcessor} is a system job that touches all
 * tenants; the {@code pickPending} query bypasses Hibernate's
 * {@code @TenantId} filter with a native query (same pattern as
 * {@code AiSweeper}). Multi-tenant is still respected because
 * {@code @SQLRestriction("deleted = false")} still applies to the
 * entity (we don't soft-delete in the outbox anyway).</p>
 */
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    /**
     * Pick a batch of PENDING rows whose {@code next_retry_at <= now},
     * oldest first. Used by the {@code @Scheduled} processor.
     *
     * <p><b>System query — bypasses {@code @TenantId}.</b> The processor
     * iterates rows across all tenants; tenant scope is preserved via
     * the {@code tenant_id} column on each row.</p>
     */
    @Query(value = """
            SELECT * FROM edushift.email_outbox
            WHERE status = 'PENDING'
              AND next_retry_at <= :now
              AND deleted = false
            ORDER BY next_retry_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EmailOutbox> pickPending(
            @Param("now") Instant now,
            @Param("limit") int limit);

    /** Count of PENDING rows across all tenants (for ops dashboard). */
    @Query(value = """
            SELECT COUNT(*) FROM edushift.email_outbox
            WHERE status = 'PENDING' AND deleted = false
            """, nativeQuery = true)
    long countPending();
}
