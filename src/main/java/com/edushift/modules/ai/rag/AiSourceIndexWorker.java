package com.edushift.modules.ai.rag;

import com.edushift.modules.files.entity.FileUploadStatus;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.files.storage.StorageService;
import com.edushift.shared.exception.ApiException;
import com.edushift.shared.multitenancy.TenantContext;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Claims pending files in PostgreSQL so work survives HTTP timeouts and restarts. */
@Service
public class AiSourceIndexWorker {

    private static final Logger log = LoggerFactory.getLogger(AiSourceIndexWorker.class);
    private static final int MAX_FILE_BYTES = 25_000_000;

    private final JdbcTemplate jdbc;
    private final FileObjectService files;
    private final StorageService storage;
    private final AiSourceTextExtractor extractor;
    private final AiSourceIndexWriter writer;
    private final Executor executor;
    private final TransactionTemplate transactionTemplate;

    public AiSourceIndexWorker(JdbcTemplate jdbc, FileObjectService files,
                               StorageService storage, AiSourceTextExtractor extractor,
                               AiSourceIndexWriter writer,
                               @Qualifier("aiSourceIndexExecutor") Executor executor,
                               PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.files = files;
        this.storage = storage;
        this.extractor = extractor;
        this.writer = writer;
        this.executor = executor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.ai.source-index-poll-ms:15000}")
    public void processPending() {
        for (int i = 0; i < 5; i++) {
            Job job = claim();
            if (job == null) return;
            executor.execute(() -> process(job));
        }
    }

    private void process(Job job) {
        TenantContext.runAs(job.tenantId(), () -> {
            try {
                var file = files.findByPublicUuid(job.filePublicUuid())
                        .orElseThrow(() -> new IllegalStateException("AI source file missing"));
                if (file.getStatus() != FileUploadStatus.READY) {
                    throw new IllegalStateException("AI source file is not ready");
                }
                byte[] bytes;
                try (InputStream in = storage.open(job.tenantId(), file.getRemoteKey())) {
                    bytes = in.readNBytes(MAX_FILE_BYTES + 1);
                }
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new IllegalStateException("AI source file exceeds 25 MB");
                }
                AiSourceTextExtractor.ExtractionResult extraction =
                        extractor.extractWithDiagnostics(bytes, file.getContentType());
                log.info("AI source extracted tenant={} source={} totalPages={} digitalTextPages={} ocrPages={} ocrFailedPages={} ocrSkippedPages={} unreadablePages={} chars={}",
                        job.tenantId(), job.publicUuid(), extraction.totalPages(),
                        extraction.digitalTextPages(), extraction.ocrPages(),
                        extraction.ocrFailedPages(), extraction.ocrSkippedPages(),
                        extraction.unreadablePages(),
                        extraction.pages().stream().mapToLong(page -> page.text().length()).sum());
                if (!extraction.ocrFailedPages().isEmpty() || !extraction.ocrSkippedPages().isEmpty()
                        || !extraction.unreadablePages().isEmpty()) {
                    log.warn("AI source has pages requiring manual review tenant={} source={} ocrFailedPages={} ocrSkippedPages={} unreadablePages={}",
                            job.tenantId(), job.publicUuid(), extraction.ocrFailedPages(),
                            extraction.ocrSkippedPages(), extraction.unreadablePages());
                }
                writer.complete(job.publicUuid(), extraction.pages());
            } catch (Exception ex) {
                log.warn("AI source indexing failed for tenant={} source={}: {}",
                        job.tenantId(), job.publicUuid(), ex.toString());
                try {
                    String code = ex instanceof ApiException apiException
                            ? apiException.getCode() : "AI_SOURCE_INDEX_FAILED";
                    writer.fail(job.publicUuid(), code);
                } catch (RuntimeException writeFailure) {
                    log.error("Could not persist AI source failure for source={}",
                            job.publicUuid(), writeFailure);
                }
            }
            return null;
        });
    }

    private Job claim() {
        return transactionTemplate.execute(transactionStatus -> jdbc.query("""
                UPDATE edushift.ai_source_documents d
                   SET status = 'PROCESSING', updated_at = now()
                 WHERE d.deleted = false
                   AND (d.status = 'INDEXING'
                        OR (d.status = 'PROCESSING'
                            AND d.updated_at < now() - interval '1 hour'))
                   AND d.id = (
                    SELECT id FROM edushift.ai_source_documents
                     WHERE deleted = false
                       AND (status = 'INDEXING'
                            OR (status = 'PROCESSING'
                                AND updated_at < now() - interval '1 hour'))
                     ORDER BY updated_at, id
                     FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING d.tenant_id, d.public_uuid, d.file_public_uuid
                """, rs -> rs.next()
                ? new Job(rs.getObject("tenant_id", UUID.class),
                          rs.getObject("public_uuid", UUID.class),
                          rs.getObject("file_public_uuid", UUID.class))
                : null));
    }

    private record Job(UUID tenantId, UUID publicUuid, UUID filePublicUuid) {}
}
