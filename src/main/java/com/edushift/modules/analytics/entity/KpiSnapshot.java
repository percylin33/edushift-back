package com.edushift.modules.analytics.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only snapshot of a tenant KPI. Refresh = INSERT a new row.
 * <p>
 * Idempotency for the {@code @Scheduled} cache-warming job is enforced
 * by the {@code uk_kpi_snapshots_idempotency} constraint on
 * {@code (tenant_id, metric_key, period_start, period_end, dimensions_hash)}.
 */
@Entity
@Table(name = "kpi_snapshots", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class KpiSnapshot extends TenantAwareEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "metric_key", nullable = false, length = 64)
	private MetricKey metricKey;

	@Column(name = "period_start", nullable = false)
	private Instant periodStart;

	@Column(name = "period_end", nullable = false)
	private Instant periodEnd;

	@Column(name = "value_numeric", precision = 18, scale = 6)
	private BigDecimal valueNumeric;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "dimensions", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> dimensions;

	@Column(name = "dimensions_hash", nullable = false, length = 64)
	private String dimensionsHash;

	@Column(name = "computed_at", nullable = false)
	private Instant computedAt;
}