package com.edushift.modules.reports.dto;

import com.edushift.modules.reports.entity.ReportTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body for a {@link ReportTemplate} row.
 */
public record ReportTemplateResponse(
		UUID publicUuid,
		String name,
		String description,
		ReportTemplate.ReportType reportType,
		ReportTemplate.Format format,
		boolean active,
		String cronExpression,
		String timezone,
		Instant lastRunAt,
		Instant nextRunAt,
		List<String> recipients,
		String emailSubject,
		String emailBodyTemplate,
		String params,
		Instant createdAt,
		Instant updatedAt
) {
	public static ReportTemplateResponse from(ReportTemplate t) {
		return new ReportTemplateResponse(
				t.getPublicUuid(),
				t.getName(),
				t.getDescription(),
				t.getReportType(),
				t.getFormat(),
				t.isActive(),
				t.getCronExpression(),
				t.getTimezone(),
				t.getLastRunAt(),
				t.getNextRunAt(),
				t.getRecipients(),
				t.getEmailSubject(),
				t.getEmailBodyTemplate(),
				t.getParams(),
				t.getCreatedAt(),
				t.getUpdatedAt());
	}
}