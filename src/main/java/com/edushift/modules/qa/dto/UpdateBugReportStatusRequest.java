package com.edushift.modules.qa.dto;

import com.edushift.modules.qa.QaBugReport;
import jakarta.validation.constraints.NotNull;

/**
 * Body for {@code PATCH /api/v1/qa/bug-reports/{publicUuid}/status}.
 */
public record UpdateBugReportStatusRequest(
        @NotNull QaBugReport.Status status
) {}