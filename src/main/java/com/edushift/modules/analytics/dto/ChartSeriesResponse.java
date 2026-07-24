package com.edushift.modules.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Time-series payload for chart endpoints
 * ({@code GET /v1/analytics/charts/{attendance|performance|morosidad}}).
 *
 * @param metricKey  identifier
 * @param points     ordered oldest → newest
 */
public record ChartSeriesResponse(
		String metricKey,
		List<Point> points
) {
	public record Point(Instant periodStart, Instant periodEnd, BigDecimal value) {
	}
}