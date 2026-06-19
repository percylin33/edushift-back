package com.edushift.modules.reports.job;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Status;
import com.edushift.modules.reports.repository.ReportJobRepository;
import com.edushift.modules.reports.service.ReportService;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report job processor (Sprint 9 / BE-9.2).
 *
 * <p>Scheduled job that picks up {@code PENDING} jobs (oldest first,
 * SKIP LOCKED to allow multiple workers) and runs the requested
 * generator. On success it stores the output bytes in
 * {@code file_objects} (bucket {@code reports}) and marks the job
 * {@code DONE}.</p>
 *
 * <p>For MVP we DON'T upload the file to storage — we keep the bytes
 * in a transient cache and expose them via
 * {@code GET /reports/jobs/{publicUuid}/download}. The storage
 * integration is documented in
 * {@code docs/modules/reports.md} (TODO: write when module is
 * documented).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportJobProcessor {

    private final ReportJobRepository jobRepo;
    private final ReportService reportService;

    @Value("${app.reports.batch-size:5}")
    private int batchSize;

    /**
     * Run every 15s. Tighter than the email outbox (reports are
     * user-initiated, not volume-driven, so a faster loop is friendlier).
     */
    @Scheduled(fixedDelayString = "${app.reports.process-interval:15000}",
               initialDelay = 10_000)
    public void process() {
        List<ReportJob> batch = jobRepo.pickPending(batchSize);
        if (batch.isEmpty()) return;
        log.info("[ReportProcessor] processing batch of {} jobs", batch.size());
        for (ReportJob job : batch) {
            processOne(job.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(UUID id) {
        jobRepo.findById(id).ifPresent(this::run);
    }

    private void run(ReportJob job) {
        try {
            // Set the tenant context for the query.
            TenantContext.set(job.getTenantId());
            try {
                job.setStatus(Status.RUNNING);
                job.setStartedAt(Instant.now());
                job.setProgressPct((short) 25);
                jobRepo.save(job);

                byte[] bytes = reportService.generateBytes(job);

                job.setProgressPct((short) 90);
                job.setStatus(Status.DONE);
                job.setFinishedAt(Instant.now());
                job.setProgressPct((short) 100);
                job.setErrorCode(null);
                job.setErrorMessage(null);
                jobRepo.save(job);

                ReportOutputCache.put(job.getPublicUuid(), bytes, job.getFormat());
                log.info("[ReportProcessor] done job={} type={} format={} bytes={}",
                        job.getPublicUuid(), job.getReportType(), job.getFormat(), bytes.length);
            } finally {
                TenantContext.clear();
            }
        } catch (Exception ex) {
            job.setStatus(Status.FAILED);
            job.setFinishedAt(Instant.now());
            job.setErrorCode("GENERATION_FAILED");
            job.setErrorMessage(truncate(ex.getMessage(), 2000));
            jobRepo.save(job);
            log.error("[ReportProcessor] failed job={}: {}", job.getPublicUuid(), ex.getMessage(), ex);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
