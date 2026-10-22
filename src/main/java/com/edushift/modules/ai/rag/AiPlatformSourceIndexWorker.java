package com.edushift.modules.ai.rag;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.files.storage.StorageService;
import com.edushift.shared.exception.ApiException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class AiPlatformSourceIndexWorker {

    private static final int MAX_FILE_BYTES = 25_000_000;
    private static final long IDLE_LOG_INTERVAL_POLLS = 4;

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final AiSourceTextExtractor extractor;
    private final AiPlatformSourceIndexWriter writer;
    private final Executor executor;
    private final TransactionTemplate transactionTemplate;
    private final AtomicLong idlePollCount = new AtomicLong();

    public AiPlatformSourceIndexWorker(JdbcTemplate jdbc, StorageService storage,
            AiSourceTextExtractor extractor, AiPlatformSourceIndexWriter writer,
            @Qualifier("aiSourceIndexExecutor") Executor executor,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.extractor = extractor;
        this.writer = writer;
        this.executor = executor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.ai.source-index-poll-ms:15000}")
    public void processPending() {
        for (int i = 0; i < 2; i++) {
            Job job;
            try {
                job = claim();
            } catch (RuntimeException ex) {
                log.error("EduShift platform RAG queue poll failed", ex);
                return;
            }
            if (job == null) {
                if (idlePollCount.incrementAndGet() % IDLE_LOG_INTERVAL_POLLS == 0) {
                    log.info("EduShift platform RAG index worker is running; no queued sources");
                }
                return;
            }
            idlePollCount.set(0);
            log.info("EduShift platform RAG source claimed source={}", job.publicUuid());
            try {
                executor.execute(() -> process(job));
            } catch (RuntimeException ex) {
                log.error("EduShift platform RAG source dispatch failed source={}",
                        job.publicUuid(), ex);
            }
        }
    }

    private void process(Job job) {
        long jobStarted = System.nanoTime();
        log.info("EduShift platform RAG source processing started source={} contentType={}",
                job.publicUuid(), job.contentType());
        try {
            byte[] bytes;
            long storageStarted = System.nanoTime();
            try (InputStream in = storage.open(TenantIdResolver.SUPER_ADMIN_SENTINEL,
                    job.remoteKey())) {
                bytes = in.readNBytes(MAX_FILE_BYTES + 1);
            }
            log.info("EduShift platform RAG source downloaded source={} bytes={} elapsedMs={}",
                    job.publicUuid(), bytes.length, elapsedMs(storageStarted));
            if (bytes.length > MAX_FILE_BYTES) {
                throw new IllegalStateException("Platform source exceeds 25 MB");
            }
            long extractionStarted = System.nanoTime();
            AiSourceTextExtractor.ExtractionResult extraction =
                    extractor.extractWithDiagnostics(bytes, job.contentType());
            List<AiSourceTextExtractor.PageText> pages = extraction.pages();
            long extractedChars = pages.stream().mapToLong(page -> page.text().length()).sum();
            log.info("EduShift platform RAG source extracted source={} contentType={} totalPages={} digitalTextPages={} ocrPages={} ocrFailedPages={} ocrSkippedPages={} unreadablePages={} chars={} elapsedMs={}",
                    job.publicUuid(), job.contentType(), extraction.totalPages(),
                    extraction.digitalTextPages(), extraction.ocrPages(),
                    extraction.ocrFailedPages(), extraction.ocrSkippedPages(),
                    extraction.unreadablePages(), extractedChars,
                    elapsedMs(extractionStarted));
            if (!extraction.ocrFailedPages().isEmpty() || !extraction.ocrSkippedPages().isEmpty()
                    || !extraction.unreadablePages().isEmpty()) {
                log.warn("EduShift platform RAG source has pages requiring manual review source={} ocrFailedPages={} ocrSkippedPages={} unreadablePages={}",
                        job.publicUuid(), extraction.ocrFailedPages(), extraction.ocrSkippedPages(),
                        extraction.unreadablePages());
            }

            long writeStarted = System.nanoTime();
            AiPlatformSourceIndexWriter.IndexResult result = writer.complete(job.publicUuid(), pages);
            if (result.completed()) {
                log.info("EduShift platform RAG source indexed source={} status={} chunks={} textBytes={} elapsedMs={} writeElapsedMs={}",
                        job.publicUuid(), result.status(), result.chunks(), result.textBytes(),
                        elapsedMs(jobStarted), elapsedMs(writeStarted));
            } else {
                log.warn("EduShift platform RAG source indexing skipped source={} currentStatus={} elapsedMs={}",
                        job.publicUuid(), result.status(), elapsedMs(jobStarted));
            }
        } catch (Exception ex) {
            String code = ex instanceof ApiException apiException
                    ? apiException.getCode() : "AI_PLATFORM_SOURCE_INDEX_FAILED";
            log.error("EduShift platform RAG source indexing failed source={} errorCode={} elapsedMs={}",
                    job.publicUuid(), code, elapsedMs(jobStarted), ex);
            try {
                writer.fail(job.publicUuid(), code);
            } catch (RuntimeException writeFailure) {
                log.error("Could not persist EduShift source failure for source={}",
                        job.publicUuid(), writeFailure);
            }
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private Job claim() {
        return transactionTemplate.execute(transactionStatus -> jdbc.query("""
                UPDATE edushift.ai_platform_source_documents d
                   SET status = 'PROCESSING', updated_at = now()
                 WHERE d.deleted = false
                   AND (d.status = 'INDEXING'
                        OR (d.status = 'PROCESSING'
                            AND d.updated_at < now() - interval '1 hour'))
                   AND d.id = (
                    SELECT id FROM edushift.ai_platform_source_documents
                     WHERE deleted = false
                       AND (status = 'INDEXING'
                            OR (status = 'PROCESSING'
                                AND updated_at < now() - interval '1 hour'))
                     ORDER BY updated_at, id
                     FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING d.public_uuid, d.storage_remote_key, d.content_type
                """, rs -> rs.next()
                ? new Job(rs.getObject("public_uuid", UUID.class),
                        rs.getString("storage_remote_key"), rs.getString("content_type"))
                : null));
    }

    private record Job(UUID publicUuid, String remoteKey, String contentType) {}
}
