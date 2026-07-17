package com.edushift.modules.help.entity;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User-submitted feedback against a chapter of a role manual. Nullable
 * {@code chapterFile} means the feedback is on the manual as a whole
 * (the user is on the index or README).
 *
 * <p>Append-only from the user's POV — status transitions are an admin
 * concern handled by a separate process (not exposed in this sprint).</p>
 */
@Entity
@Table(
        name = "help_feedback",
        schema = "edushift",
        uniqueConstraints = @UniqueConstraint(name = "uk_help_feedback_public_uuid", columnNames = "public_uuid")
)
@Getter
@Setter
@NoArgsConstructor
public class HelpFeedback extends AuditableEntity {

    @Column(name = "public_uuid", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID publicUuid;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    /** Nullable — null means the feedback is on the manual as a whole. */
    @Column(name = "chapter_file", length = 64)
    private String chapterFile;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FeedbackStatus status = FeedbackStatus.OPEN;

    public enum FeedbackStatus {
        OPEN, ACKNOWLEDGED, RESOLVED
    }

    @PrePersist
    private void onPrePersist() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
    }
}