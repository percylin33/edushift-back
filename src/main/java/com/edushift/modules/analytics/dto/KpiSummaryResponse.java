package com.edushift.modules.analytics.dto;

import java.util.List;

/**
 * Aggregated KPI snapshot for the current period.
 * Returned by {@code GET /v1/analytics/kpis}.
 */
public record KpiSummaryResponse(
		List<KpiResponse> kpis
) {
}