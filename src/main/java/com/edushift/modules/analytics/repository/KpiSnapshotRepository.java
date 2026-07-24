package com.edushift.modules.analytics.repository;

import com.edushift.modules.analytics.entity.KpiSnapshot;
import com.edushift.modules.analytics.entity.MetricKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KpiSnapshotRepository extends JpaRepository<KpiSnapshot, UUID> {

	/**
	 * Latest snapshot for a given (tenant, metric, dimensions).
	 * Used by the {@code GET /v1/analytics/kpis} endpoint to skip
	 * recomputation when the cache is fresh.
	 */
	@Query("""
			SELECT k FROM KpiSnapshot k
			WHERE k.tenantId = :tenantId
			  AND k.metricKey = :metricKey
			  AND k.dimensionsHash = :dimensionsHash
			ORDER BY k.computedAt DESC
			""")
	List<KpiSnapshot> findLatestByMetric(
			@Param("tenantId") UUID tenantId,
			@Param("metricKey") MetricKey metricKey,
			@Param("dimensionsHash") String dimensionsHash,
			org.springframework.data.domain.Pageable pageable);

	default Optional<KpiSnapshot> findMostRecent(UUID tenantId, MetricKey metric, String dimsHash) {
		List<KpiSnapshot> rows = findLatestByMetric(tenantId, metric, dimsHash,
				org.springframework.data.domain.PageRequest.of(0, 1));
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}

	/**
	 * Series for chart endpoints ordered oldest → newest so the FE can
	 * plot directly without reversing.
	 */
	List<KpiSnapshot> findByTenantIdAndMetricKeyAndPeriodStartBetweenOrderByPeriodStartAsc(
			UUID tenantId, MetricKey metricKey, Instant from, Instant to);

	boolean existsByTenantIdAndMetricKeyAndPeriodStartAndPeriodEndAndDimensionsHash(
			UUID tenantId, MetricKey metricKey, Instant periodStart, Instant periodEnd, String dimensionsHash);
}