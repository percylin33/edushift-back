package com.edushift.modules.qa.dto;

import com.edushift.modules.qa.QaBugReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Body for {@code POST /api/v1/qa/bug-reports}.
 */
public record CreateBugReportRequest(
        @NotBlank @Size(max = 200) String capabilityId,
        @NotBlank @Size(max = 200) String stepId,
        @Size(max = 500) String stepLabel,
        @NotNull QaBugReport.Severity severity,
        @Size(max = 4000) String notes,
        Map<String, Object> request
) {}