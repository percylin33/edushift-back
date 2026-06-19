package com.edushift.modules.notifications.repository;

import com.edushift.modules.notifications.entity.AnnouncementRecipient;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link AnnouncementRecipient} (Sprint 9 / BE-9.4).
 */
public interface AnnouncementRecipientRepository extends JpaRepository<AnnouncementRecipient, UUID> {

    @Query("""
            SELECT r FROM AnnouncementRecipient r
            WHERE r.userId = :userId
            ORDER BY r.deliveredAt DESC
            """)
    List<AnnouncementRecipient> findByUser(@Param("userId") UUID userId);

    @Query("""
            SELECT r FROM AnnouncementRecipient r
            WHERE r.userId = :userId AND r.readAt IS NULL
            ORDER BY r.deliveredAt DESC
            """)
    List<AnnouncementRecipient> findUnreadByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("""
            UPDATE AnnouncementRecipient r
            SET r.readAt = CURRENT_TIMESTAMP
            WHERE r.announcementId = :announcementId
              AND r.userId = :userId
              AND r.readAt IS NULL
            """)
    int markRead(@Param("announcementId") UUID announcementId, @Param("userId") UUID userId);
}
