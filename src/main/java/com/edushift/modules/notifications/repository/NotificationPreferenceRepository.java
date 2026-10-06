package com.edushift.modules.notifications.repository;

import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.NotificationPreference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link NotificationPreference} (Sprint 9 / BE-9.1).
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    /** Lookup for a specific (user, channel, category) triple. */
    Optional<NotificationPreference> findByUserIdAndChannelAndCategory(
            UUID userId, Channel channel, Category category);

    /**
     * Both channels of one category for a fan-out batch. Callers skip
     * this when {@code userIds} is empty.
     */
    @Query("""
            SELECT p FROM NotificationPreference p
            WHERE p.userId IN :userIds
              AND p.category = :category
            """)
    List<NotificationPreference> findAllByUserIdInAndCategory(
            @Param("userIds") Collection<UUID> userIds,
            @Param("category") Category category);

    /** All preferences for a user (used by the FE-9.4 matrix). */
    @Query("""
            SELECT p FROM NotificationPreference p
            WHERE p.userId = :userId
            ORDER BY p.channel ASC, p.category ASC
            """)
    List<NotificationPreference> findAllByUser(@Param("userId") UUID userId);
}
