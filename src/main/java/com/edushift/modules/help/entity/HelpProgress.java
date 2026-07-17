package com.edushift.modules.help.entity;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per (user, role, chapter file, item id) capturing whether the
 * user has marked that autoevaluación checklist item as done.
 *
 * <p>The {@code itemId} is a deterministic identifier derived from the
 * checklist text by the FE (SHA-256 hash, truncated). The BE never
 * interprets the ID beyond using it as a stable opaque key.</p>
 */
@Entity
@Table(
        name = "help_progress",
        schema = "edushift",
        uniqueConstraints = @UniqueConstraint(name = "uk_help_progress_public_uuid", columnNames = "public_uuid")
)
@Getter
@Setter
@NoArgsConstructor
public class HelpProgress extends AuditableEntity {

    @Column(name = "public_uuid", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID publicUuid;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "chapter_file", nullable = false, length = 64)
    private String chapterFile;

    @Column(name = "item_id", nullable = false, length = 64)
    private String itemId;

    @Column(name = "checked", nullable = false)
    private boolean checked;

    /** Mirror of {@code deleted} for JPQL {@code SET deletedAt = ...} queries. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    private void onPrePersist() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
    }
}