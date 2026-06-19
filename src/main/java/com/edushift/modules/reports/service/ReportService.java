package com.edushift.modules.reports.service;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.entity.ReportJob.Status;
import com.edushift.modules.reports.generator.CsvReportGenerator;
import com.edushift.modules.reports.generator.PdfReportGenerator;
import com.edushift.modules.reports.generator.XlsxReportGenerator;
import com.edushift.modules.reports.repository.ReportJobRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report service (Sprint 9 / BE-9.2).
 *
 * <p>The {@link #request} method is the only entry point. It:
 * <ol>
 *   <li>Resolves the idempotency key (returns the existing job if found).</li>
 *   <li>Persists a new {@link ReportJob} in PENDING state.</li>
 *   <li>Returns the job's {@code publicUuid} so the FE can poll for status.</li>
 * </ol>
 *
 * <p>Actual generation happens in {@link com.edushift.modules.reports.job.ReportJobProcessor}
 * (a separate scheduled job) and writes the bytes to
 * {@code file_objects}. The service just orchestrates metadata.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportJobRepository jobRepo;
    private final CsvReportGenerator csvGen;
    private final XlsxReportGenerator xlsxGen;
    private final PdfReportGenerator pdfGen;

    @Transactional
    public ReportJob request(UUID userId, ReportType type, Format format,
                              String params, String idemKey) {
        // Idempotency: return existing job if the same key already exists.
        if (idemKey != null && !idemKey.isBlank()) {
            Optional<ReportJob> existing = jobRepo.findByIdemKey(userId, idemKey);
            if (existing.isPresent()) {
                log.info("[Reports] idempotent hit key={} job={}", idemKey, existing.get().getPublicUuid());
                return existing.get();
            }
        }
        ReportJob job = new ReportJob();
        job.setTenantId(TenantContext.currentRequired());
        job.setRequestedByUserId(userId);
        job.setReportType(type);
        job.setFormat(format);
        job.setParams(params == null ? "{}" : params);
        job.setIdemKey(idemKey == null ? "" : idemKey);
        job.setStatus(Status.PENDING);
        job.setRequestedAt(Instant.now());
        job.setExpiresAt(Instant.now().plusSeconds(600));
        return jobRepo.save(job);
    }

    @Transactional(readOnly = true)
    public ReportJob get(UUID publicUuid) {
        return jobRepo.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new com.edushift.shared.exception.NotFoundException(
                        "REPORT_JOB_NOT_FOUND",
                        "Report job not found in the current tenant"));
    }

    /**
     * Generate the bytes of a job. Called by the processor. Returns
     * the raw bytes (caller is responsible for uploading to storage).
     */
    public byte[] generateBytes(ReportJob job) throws IOException {
        return switch (job.getFormat()) {
            case CSV  -> csvGen.generate(job);
            case XLSX -> xlsxGen.generate(job);
            case PDF  -> pdfGen.generate(job);
        };
    }
}
