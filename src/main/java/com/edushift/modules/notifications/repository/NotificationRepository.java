package com.edushift.modules.notifications.repository;

import com.edushift.modules.notifications.entity.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link Notification} (Sprint 9 / BE-9.1).
 *
 * <p>Tenant-scoped automatically by Hibernate {@code @TenantId}.
 * The {@code recipient_user_id} is supplied by the caller; we do NOT
 * add a method that takes {@code tenantId} explicitly (anti-enumeration).</p>
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * List the recipient's notifications, newest first. Used by the
     * bell dropdown and the {@code /notifications} page.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientUserId = :userId
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findByRecipient(@Param("userId") UUID userId, Pageable pageable);

    /** Same as above but only unread (status not in READ/SKIPPED/FAILED). */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientUserId = :userId
              AND n.status NOT IN (com.edushift.modules.notifications.entity.Notification.Status.READ,
                                   com.edushift.modules.notifications.entity.Notification.Status.SKIPPED,
                                   com.edushift.modules.notifications.entity.Notification.Status.FAILED)
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findUnreadByRecipient(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Count of unread notifications for the bell badge. Cheap because
     * of the partial index {@code idx_notifications_tenant_recipient_unread}.
     */
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipientUserId = :userId
              AND n.status NOT IN (com.edushift.modules.notifications.entity.Notification.Status.READ,
                                   com.edushift.modules.notifications.entity.Notification.Status.SKIPPED,
                                   com.edushift.modules.notifications.entity.Notification.Status.FAILED)
            """)
    long countUnreadByRecipient(@Param("userId") UUID userId);

    Optional<Notification> findByPublicUuid(UUID publicUuid);

    java.util.List<Notification> findAllByPublicUuidIn(java.util.Collection<UUID> publicUuids);

    /** Mark a single notification as READ (only the recipient can). */
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.status = com.edushift.modules.notifications.entity.Notification.Status.READ,
                n.readAt = CURRENT_TIMESTAMP
            WHERE n.publicUuid = :publicUuid
              AND n.recipientUserId = :userId
              AND n.status <> com.edushift.modules.notifications.entity.Notification.Status.READ
            """)
    int markRead(@Param("publicUuid") UUID publicUuid, @Param("userId") UUID userId);

    /** Mark all as read in bulk (used by the "mark all" button). */
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.status = com.edushift.modules.notifications.entity.Notification.Status.READ,
                n.readAt = CURRENT_TIMESTAMP
            WHERE n.recipientUserId = :userId
              AND n.status NOT IN (com.edushift.modules.notifications.entity.Notification.Status.READ,
                                   com.edushift.modules.notifications.entity.Notification.Status.SKIPPED,
                                   com.edushift.modules.notifications.entity.Notification.Status.FAILED)
            """)
    int markAllRead(@Param("userId") UUID userId);
}
