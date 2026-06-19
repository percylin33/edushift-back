package com.edushift.modules.reports.controller;

import com.edushift.modules.reports.dto.CreateReportRequest;
import com.edushift.modules.reports.dto.ReportJobResponse;
import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.job.ReportOutputCache;
import com.edushift.modules.reports.service.ReportService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report REST controller (Sprint 9 / BE-9.2).
 *
 * <p>Two flows:</p>
 * <ol>
 *   <li>POST /api/v1/reports — enqueue a job, return its publicUuid.</li>
 *   <li>GET /api/v1/reports/{publicUuid} — poll status (or download
 *       if the job is DONE).</li>
 * </ol>
 *
 * <p>Downloads are served by {@code GET .../download} which returns
 * the cached bytes with the right Content-Type and filename.</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;

    private UUID me() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ReportJobResponse> create(
            @Valid @RequestBody CreateReportRequest req) {
        ReportJob job = reportService.request(
                me(), req.reportType(), req.format(),
                req.params(), req.idemKey());
        return ApiResponse.ok(ReportJobResponse.from(job));
    }

    @GetMapping("/{publicUuid}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ReportJobResponse> get(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(ReportJobResponse.from(reportService.get(publicUuid)));
    }

    @GetMapping("/{publicUuid}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(@PathVariable UUID publicUuid) {
        ReportJob job = reportService.get(publicUuid);
        if (job.getStatus() != com.edushift.modules.reports.entity.ReportJob.Status.DONE) {
            throw new com.edushift.shared.exception.BusinessException(
                    "REPORT_NOT_READY",
                    "Report is not ready yet; current status: " + job.getStatus());
        }
        ReportOutputCache.Entry entry = ReportOutputCache.get(publicUuid);
        if (entry == null) {
            throw new NotFoundException("REPORT_OUTPUT_EXPIRED",
                    "Report output has expired; please re-request");
        }
        MediaType mt = switch (entry.format()) {
            case PDF  -> MediaType.APPLICATION_PDF;
            case XLSX -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case CSV  -> MediaType.parseMediaType("text/csv");
        };
        String filename = job.getReportType().name().toLowerCase() + "-"
                + publicUuid + "." + entry.format().name().toLowerCase();
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(entry.bytes());
    }
}
