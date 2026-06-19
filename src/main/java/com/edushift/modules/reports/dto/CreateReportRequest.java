package com.edushift.modules.reports.dto;

import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request a new report job (Sprint 9 / BE-9.2).
 *
 * <p>{@code idemKey} is optional; if supplied, the second POST
 * with the same key returns the original job. Recommended format:
 * {@code "gradebook-section-{sectionUuid}-period-{period}-at-{date}"}.</p>
 */
public record CreateReportRequest(
        @NotNull ReportType reportType,
        @NotNull Format format,
        @Size(max = 8000) String params,
        @Size(max = 80) String idemKey
) {}
