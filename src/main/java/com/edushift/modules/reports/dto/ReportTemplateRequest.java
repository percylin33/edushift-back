package com.edushift.modules.reports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for {@code POST /v1/reports/templates} and
 * {@code PUT  /v1/reports/templates/{uuid}}.
 *
 * <p>Validation notes:
 * <ul>
 *   <li>{@code cronExpression} — Spring CronExpression format
 *       (6 fields: {@code sec min hour dom mon dow}). The service
 *       validates the expression by trying to {@code parse} it; an
 *       unparseable cron returns 400 {@code REPORT_TEMPLATE_BAD_CRON}.</li>
 *   <li>{@code timezone} — IANA id; the service falls back to
 *       {@code "America/Lima"} when missing.</li>
 *   <li>{@code recipients} — at least one is recommended but not
 *       required; empty array means "no email, just generate the
 *       file" which is useful for tenants that only consume the
 *       catalog UI.</li>
 *   <li>{@code params} — JSON string pass-through to the
 *       {@code ReportJob}. The service does not parse it; the
 *       generator validates per its own schema.</li>
 * </ul>
 */
public record ReportTemplateRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 500) String description,
		@NotNull com.edushift.modules.reports.entity.ReportTemplate.ReportType reportType,
		@NotNull com.edushift.modules.reports.entity.ReportTemplate.Format format,
		Boolean active,
		@NotBlank @Size(min = 5, max = 80) String cronExpression,
		@NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_+\\-/]*$") String timezone,
		@NotEmpty List<@NotBlank @Size(max = 254) String> recipients,
		@Size(max = 200) String emailSubject,
		String emailBodyTemplate,
		@NotNull String params
) {
}