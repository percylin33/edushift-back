package com.edushift.modules.qa.repository;

import com.edushift.modules.qa.QaBugReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QaBugReportRepository extends JpaRepository<QaBugReport, UUID> {

    /**
     * Native query so we can NULL-check tenant_id and pivot on actor for
     * the SUPER_ADMIN aggregate view.
     */
    @Query(value = """
            SELECT *
            FROM   edushift.qa_bug_reports
            WHERE  (:tenantId IS NULL OR tenant_id = :tenantId)
              AND  (:actorId IS NOT NULL AND actor_id = :actorId)
              AND  (:capabilityId IS NULL OR capability_id = :capabilityId)
              AND  (:status IS NULL OR status = :status)
            ORDER  BY created_at DESC
            """,
            countQuery = """
            SELECT count(*)
            FROM   edushift.qa_bug_reports
            WHERE  (:tenantId IS NULL OR tenant_id = :tenantId)
              AND  (:actorId IS NOT NULL AND actor_id = :actorId)
              AND  (:capabilityId IS NULL OR capability_id = :capabilityId)
              AND  (:status IS NULL OR status = :status)
            """,
            nativeQuery = true)
    Page<QaBugReport> search(@Param("tenantId") UUID tenantId,
            @Param("actorId") UUID actorId,
            @Param("capabilityId") String capabilityId,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT COUNT(r) FROM QaBugReport r WHERE r.tenantId = :tenantId AND r.status = 'OPEN'")
    long countOpenForTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT r.capabilityId AS capabilityId, COUNT(r) AS total FROM QaBugReport r " +
           "WHERE r.tenantId = :tenantId GROUP BY r.capabilityId")
    List<java.util.Map<String, Object>> aggregateByCapability(@Param("tenantId") UUID tenantId);
}