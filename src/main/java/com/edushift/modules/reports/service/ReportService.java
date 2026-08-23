package com.edushift.modules.reports.service;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.entity.ReportJob.Status;
import com.edushift.modules.reports.generator.CsvReportGenerator;
import com.edushift.modules.reports.generator.PdfReportGenerator;
import com.edushift.modules.reports.generator.XlsxReportGenerator;
import com.edushift.modules.reports.repository.ReportJobRepository;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
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
    private final CurrentUserProvider currentUserProvider;

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
        ReportJob job = jobRepo.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new com.edushift.shared.exception.NotFoundException(
                        "REPORT_JOB_NOT_FOUND",
                        "Report job not found in the current tenant"));
        // DEBT-STUDENT-PRIVACY (Fase 0.5): GET /reports/{uuid} used to
        // be open to any authenticated caller, which let a STUDENT who
        // guessed another user's job publicUuid download their report
        // (PII). The owner-or-privileged gate makes that 403.
        assertCanRead(job);
        return job;
    }

    /**
     * Sprint cierre-C / B12 -- lightweight status poll used by
     * {@code ReportEmailDispatcher} to decide whether to send the
     * generated output. Returns only the status to avoid loading the
     * full row + the file bytes on every poll.
     */
    @Transactional(readOnly = true)
    public ReportJob.Status getStatus(UUID publicUuid) {
        return jobRepo.findByPublicUuid(publicUuid)
                .map(ReportJob::getStatus)
                .orElse(null);
    }

    /**
     * DEBT-FK-BUGS-2 / list endpoint: lista los jobs del usuario en el
     * tenant actual, paginados, mas recientes primero. Filtra por
     * {@code tenantId} para garantizar que un usuario de tenant B
     * nunca vea jobs de tenant A, aunque el FE envie el publicUuid
     * de otra tenant. {@code @TenantId} de Hibernate ya aisla; este
     * filtro explicito es defense-in-depth.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReportJob> listForUser(
            UUID userId, org.springframework.data.domain.Pageable pageable) {
        return jobRepo.findByTenantIdAndUserId(
                com.edushift.shared.multitenancy.TenantContext.currentRequired(),
                userId, pageable);
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

    /**
     * DEBT-STUDENT-PRIVACY (Fase 0.5): owner-or-privileged gate. Owner
     * is the {@code requested_by_user_id} column on the job row;
     * privileged callers are admin / super-admin (they have audit
     * dashboards that need read-all). Teachers / students / staff are
     * denied unless they own the row.
     */
    private void assertCanRead(ReportJob job) {
        UUID caller = currentUserProvider.currentUserId().orElse(null);
        if (caller == null) {
            throw new ForbiddenException("AUTH_REQUIRED",
                    "Authenticated user is required to read this report");
        }
        if (caller.equals(job.getRequestedByUserId())) {
            return;
        }
        UserRole role = currentUserProvider.currentUserRole().orElse(null);
        if (role == UserRole.TENANT_ADMIN || role == UserRole.SUPER_ADMIN) {
            return;
        }
        log.warn("[reports] ownership denied -- caller={} job={} owner={}",
                caller, job.getPublicUuid(), job.getRequestedByUserId());
        throw new ForbiddenException("REPORT_NOT_OWNED",
                "Caller is neither the owner of this report nor an admin");
    }
}
