package com.edushift.modules.reports.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Recurring report template (Sprint cierre-C / B12).
 *
 * <p>Per-tenant configurable schedule that the
 * {@code ReportTemplateRunner} ticks every minute, evaluates the
 * {@code cron_expression}, and dispatches a {@link ReportJob} when the
 * cron fires. The runner then emails the generated output to
 * {@code recipients} via {@code EmailSender}.</p>
 *
 * <p>Multi-tenant: extends {@link TenantAwareEntity}; queries
 * auto-scoped via {@code @TenantId}. Soft-delete keeps audit history
 * intact (see ADR-Cierre-C.12).</p>
 */
@Entity
@Table(name = "report_templates", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class ReportTemplate extends TenantAwareEntity {

	/** Same enum as {@link ReportJob.ReportType}; kept as a string column
	 * for forward compatibility (new types added on the BE don't need a
	 * schema migration). The CHECK constraint in V84 whitelists the
	 * current values. */
	public enum ReportType {
		GRADE_BOOK,
		ATTENDANCE_SUMMARY,
		PERIOD_CLOSE,
		STUDENT_TRANSCRIPT
	}

	/** Same enum as {@link ReportJob.Format}. */
	public enum Format {
		PDF, XLSX, CSV
	}

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "report_type", nullable = false, length = 40)
	private ReportType reportType;

	@Enumerated(EnumType.STRING)
	@Column(name = "format", nullable = false, length = 10)
	private Format format = Format.PDF;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	/** Spring cron expression (e.g. {@code "0 0 8 1 * *"}). */
	@Column(name = "cron_expression", nullable = false, length = 80)
	private String cronExpression;

	/** IANA timezone id (e.g. {@code "America/Lima"}). */
	@Column(name = "timezone", nullable = false, length = 40)
	private String timezone = "America/Lima";

	@Column(name = "last_run_at")
	private Instant lastRunAt;

	@Column(name = "next_run_at")
	private Instant nextRunAt;

	/** JSONB array — see ADR-Cierre-C.12. Empty list = no email,
	 * the generated file is still produced but stays in the catalog. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recipients", nullable = false, columnDefinition = "jsonb")
	private List<String> recipients = new java.util.ArrayList<>();

	@Column(name = "email_subject", length = 200)
	private String emailSubject;

	@Column(name = "email_body_template", columnDefinition = "text")
	private String emailBodyTemplate;

	/** JSONB pass-through to the {@code ReportJob} ({@code courseUuid},
	 * {@code periodUuid}, etc). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "params", nullable = false, columnDefinition = "jsonb")
	private String params = "{}";

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
	}
}