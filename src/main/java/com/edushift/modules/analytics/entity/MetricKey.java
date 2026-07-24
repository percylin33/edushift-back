package com.edushift.modules.analytics.entity;

/**
 * Whitelisted KPI metrics. The set is intentionally small and closed:
 * extending it requires a CHECK-constraint migration
 * (see {@code V81__create_kpi_snapshots.sql}).
 */
public enum MetricKey {
	/** PRESENT + LATE / total records in period (0..1). */
	ATTENDANCE_RATE,
	/** Average of normalized grade_records scores in period (0..1). */
	PERFORMANCE_AVG,
	/** OVERDUE / total invoices in period (0..1). */
	MOROSIDAD
}