package com.edushift.modules.reports.dto;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.entity.ReportJob.Status;
import java.time.Instant;
import java.util.UUID;

public record ReportJobResponse(
        UUID publicUuid,
        ReportType reportType,
        Format format,
        Status status,
        short progressPct,
        String errorCode,
        String errorMessage,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static ReportJobResponse from(ReportJob j) {
        return new ReportJobResponse(
                j.getPublicUuid(),
                j.getReportType(),
                j.getFormat(),
                j.getStatus(),
                j.getProgressPct(),
                j.getErrorCode(),
                j.getErrorMessage(),
                j.getRequestedAt(),
                j.getStartedAt(),
                j.getFinishedAt());
    }
}
