package com.edushift.modules.notifications.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Notification template (Sprint 9 / BE-9.1).
 *
 * <p>One row per {@code (tenant, template_key, locale)} triple. Holds the
 * subject + body HTML. Body uses {@code {{key}}} placeholders that the
 * {@code TemplateEngine} expands from the notification's
 * {@link com.edushift.modules.notifications.entity.Notification#payload() JSONB payload}.
 *
 * <h3>Editable</h3>
 * Per ADR-9.2, templates are editable at runtime by TENANT_ADMIN. The
 * body is sanitized with jsoup on write (allowlist of safe tags). Built-in
 * templates ({@code isSystem=true}) can be overridden by a tenant but the
 * base row is preserved (version=1, deleted=false) as a fallback.
 *
 * <h3>Multi-tenant</h3>
 * Extends {@link TenantAwareEntity}. Hibernate {@code @TenantId} auto-filters
 * queries. {@code @SQLRestriction("deleted = false")} from {@code BaseEntity}
 * hides soft-deleted rows.
 */
@Entity
@Table(name = "notification_templates", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplate extends TenantAwareEntity {

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    /** Stable identifier (e.g. {@code "STUDENT_ABSENT"}). See {@code NotificationEventType}. */
    @Column(name = "template_key", nullable = false, length = 60)
    private String templateKey;

    /** BCP-47 tag. MVP v1: only {@code "es-PE"}. */
    @Column(name = "locale", nullable = false, length = 10)
    private String locale = "es-PE";

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    /** HTML body. Sanitized with jsoup on write. Supports {@code {{key}}} placeholders. */
    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "version", nullable = false)
    private int version = 1;

    /**
     * Built-in template (seeded by {@code DevDataInitializer}). Cannot be
     * deleted, only overridden by a tenant-customized copy.
     */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }
}
