package com.edushift.modules.ai.rag;

import com.edushift.modules.ai.service.AiEntitlementService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.multitenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits a complete index in one tenant-scoped transaction. */
@Service
@RequiredArgsConstructor
public class AiSourceIndexWriter {

    private static final int CHUNK_CHARS = 1000;
    private static final int CHUNK_OVERLAP = 100;

    private final AiSourceDocumentRepository documents;
    private final AiSourceChunkRepository chunks;
    private final AiEntitlementService entitlements;
    private final JdbcTemplate jdbc;

    @Transactional
    public void complete(UUID publicUuid, List<AiSourceTextExtractor.PageText> pages) {
        UUID tenantId = TenantContext.currentRequired();
        var plan = entitlements.require(AiEntitlementService.RAG_CORE);
        // Serialize text-budget changes with source registration and other indexers.
        jdbc.queryForObject(
                "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))) q",
                Integer.class, tenantId.toString());
        AiSourceDocument doc = documents.findByPublicUuidForUpdate(publicUuid)
                .orElseThrow(() -> new IllegalStateException("Indexed source disappeared"));
        if (doc.getStatus() != AiSourceDocument.Status.PROCESSING) return;

        long textBytes = pages.stream().mapToLong(
                p -> p.text().getBytes(StandardCharsets.UTF_8).length).sum();
        if (documents.sumTextBytesExceptStatus(AiSourceDocument.Status.RETIRED)
                + textBytes > plan.institutionalTextBytesLimit()) {
            throw new ConflictException("AI_SOURCE_TEXT_LIMIT",
                    "El texto indexado supera el límite del plan.");
        }
        chunks.deleteForDocument(tenantId, doc.getId());
        List<AiSourceChunk> extracted = new ArrayList<>();
        int ordinal = 0;
        for (AiSourceTextExtractor.PageText page : pages) {
            String text = page.text().replaceAll("\\s+", " ").trim();
            for (int start = 0; start < text.length(); start += CHUNK_CHARS - CHUNK_OVERLAP) {
                String part = text.substring(start, Math.min(text.length(), start + CHUNK_CHARS));
                if (part.isBlank()) continue;
                AiSourceChunk chunk = new AiSourceChunk();
                chunk.setTenantId(tenantId);
                chunk.setDocumentId(doc.getId());
                chunk.setOrdinal(ordinal++);
                chunk.setPageNumber(page.pageNumber());
                chunk.setContent(part);
                extracted.add(chunk);
            }
        }
        chunks.saveAll(extracted);
        doc.setTextBytes(textBytes);
        doc.setStatus(textBytes == 0
                ? AiSourceDocument.Status.NEEDS_OCR : AiSourceDocument.Status.READY);
        doc.setIndexErrorCode(null);
        documents.save(doc);
    }

    @Transactional
    public void fail(UUID publicUuid, String code) {
        AiSourceDocument doc = documents.findByPublicUuidForUpdate(publicUuid).orElse(null);
        if (doc == null || doc.getStatus() != AiSourceDocument.Status.PROCESSING) return;
        doc.setStatus(AiSourceDocument.Status.FAILED);
        doc.setIndexErrorCode(code);
        documents.save(doc);
    }
}
