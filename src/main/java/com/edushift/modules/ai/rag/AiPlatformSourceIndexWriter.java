package com.edushift.modules.ai.rag;

import com.edushift.shared.identifier.UuidV7;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes chunks into the platform catalogue, which is intentionally outside tenant tables. */
@Service
@RequiredArgsConstructor
public class AiPlatformSourceIndexWriter {

    private static final int CHUNK_CHARS = 1000;
    private static final int CHUNK_OVERLAP = 100;
    private final JdbcTemplate jdbc;

    @Transactional
    public IndexResult complete(UUID publicUuid, List<AiSourceTextExtractor.PageText> pages) {
        UUID id = jdbc.query("""
                SELECT id FROM edushift.ai_platform_source_documents
                 WHERE public_uuid = ? AND status = 'PROCESSING' AND deleted = false
                 FOR UPDATE
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, publicUuid);
        if (id == null) {
            String currentStatus = jdbc.query("""
                    SELECT status FROM edushift.ai_platform_source_documents
                     WHERE public_uuid = ? AND deleted = false
                    """, rs -> rs.next() ? rs.getString("status") : "NOT_FOUND", publicUuid);
            return new IndexResult(false, currentStatus, 0, 0);
        }

        long textBytes = pages.stream().mapToLong(
                page -> page.text().getBytes(StandardCharsets.UTF_8).length).sum();
        jdbc.update("DELETE FROM edushift.ai_platform_source_chunks WHERE document_id = ?", id);
        List<Object[]> rows = new ArrayList<>();
        int ordinal = 0;
        for (AiSourceTextExtractor.PageText page : pages) {
            String text = page.text().replaceAll("\\s+", " ").trim();
            for (int start = 0; start < text.length(); start += CHUNK_CHARS - CHUNK_OVERLAP) {
                String content = text.substring(start, Math.min(text.length(), start + CHUNK_CHARS));
                if (!content.isBlank()) {
                    rows.add(new Object[]{UuidV7.create(), id, ordinal++, page.pageNumber(), content});
                }
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO edushift.ai_platform_source_chunks
                    (id, document_id, ordinal, page_number, content)
                VALUES (?, ?, ?, ?, ?)
                """, rows);
        int updated = jdbc.update("""
                UPDATE edushift.ai_platform_source_documents
                   SET text_bytes = ?, status = ?, index_error_code = NULL
                 WHERE id = ? AND status = 'PROCESSING'
                """, textBytes, textBytes == 0 ? "NEEDS_OCR" : "READY", id);
        if (updated != 1) {
            throw new IllegalStateException("Platform source state changed before indexing completed");
        }
        return new IndexResult(true, textBytes == 0 ? "NEEDS_OCR" : "READY", rows.size(), textBytes);
    }

    @Transactional
    public void fail(UUID publicUuid, String errorCode) {
        jdbc.update("""
                UPDATE edushift.ai_platform_source_documents
                   SET status = 'FAILED', index_error_code = ?
                 WHERE public_uuid = ? AND status = 'PROCESSING' AND deleted = false
                """, errorCode, publicUuid);
    }

    public record IndexResult(boolean completed, String status, int chunks, long textBytes) {}
}
