package com.edushift.modules.ai.rag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiSourceDocumentRepository extends JpaRepository<AiSourceDocument, UUID> {
    Optional<AiSourceDocument> findByPublicUuid(UUID publicUuid);
    Optional<AiSourceDocument> findByFilePublicUuid(UUID filePublicUuid);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from AiSourceDocument d where d.publicUuid = :publicUuid")
    Optional<AiSourceDocument> findByPublicUuidForUpdate(@Param("publicUuid") UUID publicUuid);
    boolean existsByFilePublicUuid(UUID filePublicUuid);
    List<AiSourceDocument> findAllByOrderByUpdatedAtDesc();

    @Query("select count(d) from AiSourceDocument d where d.status <> :status")
    long countExceptStatus(@Param("status") AiSourceDocument.Status status);

    @Query("select coalesce(sum(d.textBytes), 0) from AiSourceDocument d where d.status <> :status")
    long sumTextBytesExceptStatus(@Param("status") AiSourceDocument.Status status);
}
