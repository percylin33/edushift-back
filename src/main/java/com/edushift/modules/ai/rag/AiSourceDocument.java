package com.edushift.modules.ai.rag;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_source_documents", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AiSourceDocument extends TenantAwareEntity {

    public enum Kind { INSTITUTIONAL, OFFICIAL }
    public enum Status { INDEXING, PROCESSING, READY, PUBLISHED, NEEDS_OCR, FAILED, RETIRED }
    public enum Audience { STUDENT, TEACHER, FAMILY }

    @Column(name = "public_uuid", nullable = false, updatable = false)
    private UUID publicUuid;

    @Column(name = "file_public_uuid", nullable = false, updatable = false)
    private UUID filePublicUuid;

    @Column(nullable = false, length = 240)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.READY;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audiences", nullable = false, columnDefinition = "jsonb")
    private List<String> audiences;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "source_version", length = 120)
    private String sourceVersion;

    @Column(name = "effective_at")
    private LocalDate effectiveAt;

    @Column(name = "review_after")
    private LocalDate reviewAfter;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "text_bytes", nullable = false)
    private long textBytes;

    @Column(name = "index_error_code", length = 60)
    private String indexErrorCode;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }
}
