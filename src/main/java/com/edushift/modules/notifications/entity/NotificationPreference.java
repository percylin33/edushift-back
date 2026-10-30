package com.edushift.modules.notifications.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user notification preference (Sprint 9 / BE-9.1).
 *
 * <p>One row per {@code (user, channel, category)} triple. Controls
 * whether the user receives a given category through a given channel.
 * In-app and email default to enabled when no row exists. Push defaults
 * to disabled and requires an explicit opt-in.</p>
 *
 * <h3>Usage</h3>
 * The {@code NotificationService} checks this table before creating a
 * {@code Notification} row. If the user disabled (e.g.) email for GRADE,
 * the notification is created with {@code status=SKIPPED} and no email
 * is enqueued. The in-app channel is independent (so the user still
 * sees the notification in the bell).</p>
 *
 * <h3>Multi-tenant</h3>
 * Extends {@link TenantAwareEntity}; queries are auto-filtered by
 * Hibernate {@code @TenantId}.
 */
@Entity
@Table(name = "notification_preferences", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference extends TenantAwareEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private Notification.Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private Notification.Category category;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
