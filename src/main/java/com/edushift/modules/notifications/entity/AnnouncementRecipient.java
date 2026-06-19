package com.edushift.modules.notifications.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resolved recipient of an {@link Announcement} (Sprint 9 / BE-9.4).
 *
 * <p>One row per (announcement, user) pair, inserted when the
 * announcement is published. The {@code AnnouncementAudienceResolver}
 * materializes the {@code audienceType} + {@code audienceIds} into
 * concrete user rows. This trades write-time cost (resolve once) for
 * read-time simplicity ("show me my announcements" is one index scan).</p>
 */
@Entity
@Table(name = "announcement_recipients", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AnnouncementRecipient extends TenantAwareEntity {

    @Column(name = "announcement_id", nullable = false)
    private UUID announcementId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;
}
