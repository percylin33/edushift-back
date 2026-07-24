package com.edushift.modules.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single KPI scalar returned by {@code GET /v1/analytics/kpis}.
 *
 * @param metricKey       identifier (e.g. {@code ATTENDANCE_RATE})
 * @param value           primary scalar (rate 0..1 or raw avg)
 * @param computedAt      when the underlying query ran
 * @param dimensions      optional breakdown (e.g. {@code numerator}, {@code denominator},
 *                        {@code perSectionCounts}) for UI tooltips
 */
public record KpiResponse(
		String metricKey,
		BigDecimal value,
		Instant computedAt,
		java.util.Map<String, Object> dimensions
) {
}