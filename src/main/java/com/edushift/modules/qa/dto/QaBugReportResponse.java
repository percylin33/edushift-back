package com.edushift.modules.qa.dto;

import com.edushift.modules.qa.QaBugReport;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side projection of {@link QaBugReport}. Sensitive fields
 * (raw SQL, tokens) are never included — contract is additive only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QaBugReportResponse(
        UUID id,
        Instant createdAt,
        UUID tenantId,
        UUID actorId,
        String capabilityId,
        String stepId,
        String stepLabel,
        QaBugReport.Severity severity,
        QaBugReport.Status status,
        String notes,
        Map<String, Object> request
) {
    public static QaBugReportResponse of(QaBugReport r) {
        return new QaBugReportResponse(
                r.getId(),
                r.getCreatedAt(),
                r.getTenantId(),
                r.getActorId(),
                r.getCapabilityId(),
                r.getStepId(),
                r.getStepLabel(),
                r.getSeverity(),
                r.getStatus(),
                r.getNotes(),
                r.getRequest()
        );
    }
}