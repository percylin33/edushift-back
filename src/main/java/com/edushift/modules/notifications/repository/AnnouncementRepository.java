package com.edushift.modules.notifications.repository;

import com.edushift.modules.notifications.entity.Announcement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link Announcement} (Sprint 9 / BE-9.4).
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @Query("""
            SELECT a FROM Announcement a
            WHERE a.status = com.edushift.modules.notifications.entity.Announcement.Status.PUBLISHED
            ORDER BY a.publishedAt DESC
            """)
    Page<Announcement> findPublished(Pageable pageable);

    @Query("""
            SELECT a FROM Announcement a
            ORDER BY a.createdAt DESC
            """)
    Page<Announcement> findAllForAdmin(Pageable pageable);

    Optional<Announcement> findByPublicUuid(UUID publicUuid);

    /**
     * DEBT-FK-BUGS-2 / cross-tenant fix: the bare {@link #findByPublicUuid(UUID)}
     * ignores the current tenant and would happily resolve a row owned by
     * another tenant, letting admin B mutate or delete A's announcement.
     * The service-layer {@code mustFind} must use THIS variant so the
     * cross-tenant ITs ({@code deleteAsBReturns404},
     * {@code patchAsBReturns404}, {@code markReadReturns404}) get a 404
     * instead of a 204 / 400.
     */
    @Query("""
            SELECT a FROM Announcement a
            WHERE a.publicUuid = :publicUuid
              AND a.tenantId = :tenantId
            """)
    Optional<Announcement> findByPublicUuidAndTenantId(
            @Param("publicUuid") UUID publicUuid,
            @Param("tenantId") UUID tenantId);
}
