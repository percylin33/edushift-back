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

    /**
     * DEBT-STUDENT-PRIVACY (Fase 0.1): list PUBLISHED announcements that are
     * actually targeted at the given user (and tenant).
     *
     * <p>Visibility logic:</p>
     * <ul>
     *   <li>{@code audience_type = SCHOOL} → visible to every authenticated
     *       user in the tenant.</li>
     *   <li>any other audience → visible only if a row exists in
     *       {@code announcement_recipients} for the user. The
     *       {@code publish()} path already inserts one row per
     *       resolved user (via {@code AnnouncementAudienceResolver}), so
     *       this JOIN is the canonical audience filter.</li>
     * </ul>
     *
     * <p>Both predicates include the manual {@code tenant_id} check as
     * defence-in-depth even though Hibernate's {@code @TenantId} filter
     * is applied automatically.</p>
     */
    @Query("""
            SELECT DISTINCT a FROM Announcement a
            WHERE a.status = com.edushift.modules.notifications.entity.Announcement.Status.PUBLISHED
              AND a.tenantId = :tenantId
              AND (
                a.audienceType = com.edushift.modules.notifications.entity.Announcement.AudienceType.SCHOOL
                OR EXISTS (
                  SELECT 1 FROM AnnouncementRecipient r
                  WHERE r.announcementId = a.id
                    AND r.userId = :userId
                )
              )
            ORDER BY a.publishedAt DESC
            """)
    Page<Announcement> findPublishedForUser(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            Pageable pageable);

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
