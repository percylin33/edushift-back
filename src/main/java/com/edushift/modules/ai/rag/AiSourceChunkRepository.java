package com.edushift.modules.ai.rag;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiSourceChunkRepository extends JpaRepository<AiSourceChunk, UUID> {
    @Modifying
    @Query(value = "DELETE FROM edushift.ai_source_chunks WHERE tenant_id = :tenantId AND document_id = :documentId",
            nativeQuery = true)
    void deleteForDocument(@Param("tenantId") UUID tenantId,
                           @Param("documentId") UUID documentId);
}
