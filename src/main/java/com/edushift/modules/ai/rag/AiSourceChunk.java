package com.edushift.modules.ai.rag;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "ai_source_chunks", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AiSourceChunk extends TenantAwareEntity {

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;
}
