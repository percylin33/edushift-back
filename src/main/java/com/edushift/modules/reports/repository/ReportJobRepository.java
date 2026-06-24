package com.edushift.modules.reports.repository;

import com.edushift.modules.reports.entity.ReportJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link ReportJob} (Sprint 9 / BE-9.2).
 */
public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {

    Optional<ReportJob> findByPublicUuid(UUID publicUuid);

    /**
     * DEBT-FK-BUGS-2 / list endpoint: lista los jobs del usuario en su
     * tenant actual, paginados. Se usa desde {@code ReportController#list}
     * para que el FE pueda mostrar "mis reports" (no cross-tenant).
     * Filtra por {@code tenantId} explicitamente para defense-in-depth,
     * aunque el {@code @TenantId} de Hibernate ya aísla por tenant context.
     */
    @Query("""
            SELECT j FROM ReportJob j
            WHERE j.tenantId = :tenantId
              AND j.requestedByUserId = :userId
            ORDER BY j.requestedAt DESC
            """)
    org.springframework.data.domain.Page<ReportJob> findByTenantIdAndUserId(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            org.springframework.data.domain.Pageable pageable);

    /** Idempotency lookup. Empty key always misses. */
    @Query("""
            SELECT j FROM ReportJob j
            WHERE j.requestedByUserId = :userId
              AND j.idemKey = :key
            """)
    Optional<ReportJob> findByIdemKey(
            @Param("userId") UUID userId,
            @Param("key") String key);

    /** Recent jobs for the user (FE polling). */
    @Query("""
            SELECT j FROM ReportJob j
            WHERE j.requestedByUserId = :userId
            ORDER BY j.requestedAt DESC
            """)
    List<ReportJob> findRecentByUser(@Param("userId") UUID userId, org.springframework.data.domain.Pageable pageable);

    /**
     * Pick pending jobs. System query (bypasses {@code @TenantId})
     * because the processor touches all tenants.
     */
    @Query(value = """
            SELECT * FROM edushift.report_jobs
            WHERE status = 'PENDING' AND deleted = false
            ORDER BY requested_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ReportJob> pickPending(@Param("limit") int limit);

    /** Sweeper: RUNNING jobs past their expiry. */
    @Query(value = """
            SELECT * FROM edushift.report_jobs
            WHERE status = 'RUNNING' AND expires_at < :now AND deleted = false
            """, nativeQuery = true)
    List<ReportJob> findZombies(@Param("now") Instant now);
}
