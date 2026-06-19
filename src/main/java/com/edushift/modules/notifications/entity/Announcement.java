package com.edushift.modules.notifications.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Announcement (Sprint 9 / BE-9.4).
 *
 * <p>Tenant-wide rich-text message authored by {@code TENANT_ADMIN}.
 * The {@code audienceType} + {@code audienceIds} pair describes who
 * should see this announcement; the
 * {@code AnnouncementAudienceResolver} materializes that into rows in
 * {@link AnnouncementRecipient} at publish time.</p>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   DRAFT  →  SCHEDULED (if publish_at set)  →  PUBLISHED  →  ARCHIVED
 * </pre>
 * The {@code NotificationService} consumes PUBLISHED rows and dispatches
 * the in-app notification (category ANNOUNCEMENT) to each recipient.
 *
 * <h3>Multi-tenant</h3>
 * Extends {@link TenantAwareEntity}.
 */
@Entity
@Table(name = "announcements", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class Announcement extends TenantAwareEntity {

    /** Target audience. Interpretation depends on the IDs list. */
    public enum AudienceType {
        /** All users in the tenant (audienceIds empty). */
        SCHOOL,
        /** List of grade ids (e.g. {@code 1A, 2B}). */
        GRADE,
        /** List of section ids. */
        SECTION,
        /** List of course ids. */
        COURSE,
        /** List of role names (e.g. {@code TEACHER}). */
        ROLE,
        /** List of explicit user ids. */
        USER
    }

    public enum Status { DRAFT, SCHEDULED, PUBLISHED, ARCHIVED }

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 20)
    private AudienceType audienceType;

    /** Empty for {@code SCHOOL}; otherwise JSON array of UUIDs or strings. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audience_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> audienceIds = List.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.DRAFT;

    @Column(name = "publish_at")
    private Instant publishAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }
}
