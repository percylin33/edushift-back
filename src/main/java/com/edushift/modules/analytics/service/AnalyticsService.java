package com.edushift.modules.analytics.service;

import com.edushift.modules.analytics.dto.ChartSeriesResponse;
import com.edushift.modules.analytics.dto.KpiResponse;
import com.edushift.modules.analytics.dto.KpiSummaryResponse;
import com.edushift.modules.analytics.entity.KpiSnapshot;
import com.edushift.modules.analytics.entity.MetricKey;
import com.edushift.modules.analytics.repository.KpiSnapshotRepository;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only analytics service: aggregates attendance / performance /
 * morosidad KPIs from the source tables ({@code attendance_records},
 * {@code grade_records}, {@code b2b_invoices}).
 *
 * <p>Multi-tenant safety: every native query filters explicitly on
 * {@code tenant_id = :tenantId} taken from {@link TenantContext}.
 * The Hibernate {@code @TenantId} discriminator is not used here because
 * these aggregations are native SQL (subqueries / unions are awkward in JPQL).</p>
 *
 * <p>Cache strategy: {@link KpiSnapshot} rows are returned if fresh
 * (≤ {@value #CACHE_FRESH_MINUTES} min old). Otherwise the live aggregation
 * runs and a new snapshot is persisted (append-only). Refresh failures
 * never propagate — the controller still gets a value, just from the
 * live query path.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

	static final int CACHE_FRESH_MINUTES = 15;
	private static final Map<String, Object> DEFAULT_DIMENSIONS = Map.of();

	private final KpiSnapshotRepository snapshotRepository;

	@PersistenceContext
	private EntityManager em;

	@Transactional
	public KpiSummaryResponse currentSummary() {
		UUID tenantId = TenantContext.currentRequired();
		Instant now = Instant.now();
		Instant from = now.minus(30, ChronoUnit.DAYS);

		List<KpiResponse> kpis = new ArrayList<>(3);
		kpis.add(attendanceRateKpi(tenantId, from, now));
		kpis.add(performanceAvgKpi(tenantId, from, now));
		kpis.add(morosidadKpi(tenantId, from, now));
		return new KpiSummaryResponse(kpis);
	}

	@Transactional(readOnly = true)
	public ChartSeriesResponse attendanceSeries(Instant from, Instant to) {
		UUID tenantId = TenantContext.currentRequired();
		return chartFromSnapshots(tenantId, MetricKey.ATTENDANCE_RATE, from, to, "attendance");
	}

	@Transactional(readOnly = true)
	public ChartSeriesResponse performanceSeries(Instant from, Instant to) {
		UUID tenantId = TenantContext.currentRequired();
		return chartFromSnapshots(tenantId, MetricKey.PERFORMANCE_AVG, from, to, "performance");
	}

	@Transactional(readOnly = true)
	public ChartSeriesResponse morosidadSeries(Instant from, Instant to) {
		UUID tenantId = TenantContext.currentRequired();
		return chartFromSnapshots(tenantId, MetricKey.MOROSIDAD, from, to, "morosidad");
	}

	// -----------------------------------------------------------------
	// Per-KPI computation + cache
	// -----------------------------------------------------------------

	private KpiResponse attendanceRateKpi(UUID tenantId, Instant from, Instant to) {
		String dimsHash = sha256("ATTENDANCE_RATE|default");
		var cached = snapshotRepository.findMostRecent(tenantId, MetricKey.ATTENDANCE_RATE, dimsHash);
		if (cached.isPresent() && isFresh(cached.get().getComputedAt())) {
			return toKpi(cached.get());
		}
		Object[] row = (Object[]) em.createNativeQuery("""
				SELECT
					COUNT(*) FILTER (WHERE status IN ('PRESENT','LATE')) AS present_count,
					COUNT(*) AS total_count
				FROM edushift.attendance_records
				WHERE tenant_id = :tenantId
				  AND deleted = false
				  AND occurred_at >= :fromTs
				  AND occurred_at <  :toTs
				""")
				.setParameter("tenantId", tenantId)
				.setParameter("fromTs", from)
				.setParameter("toTs", to)
				.getSingleResult();
		long present = ((Number) row[0]).longValue();
		long total = ((Number) row[1]).longValue();
		BigDecimal rate = total == 0
				? BigDecimal.ZERO
				: BigDecimal.valueOf(present).divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);

		Map<String, Object> dims = new LinkedHashMap<>();
		dims.put("presentCount", present);
		dims.put("totalCount", total);
		dims.put("periodStart", from.toString());
		dims.put("periodEnd", to.toString());
		KpiSnapshot snapshot = persistSnapshot(tenantId, MetricKey.ATTENDANCE_RATE, from, to, rate, dims, dimsHash);
		return toKpi(snapshot);
	}

	private KpiResponse performanceAvgKpi(UUID tenantId, Instant from, Instant to) {
		String dimsHash = sha256("PERFORMANCE_AVG|default");
		var cached = snapshotRepository.findMostRecent(tenantId, MetricKey.PERFORMANCE_AVG, dimsHash);
		if (cached.isPresent() && isFresh(cached.get().getComputedAt())) {
			return toKpi(cached.get());
		}
		Object[] row = (Object[]) em.createNativeQuery("""
				SELECT
					AVG(score)        AS avg_score,
					COUNT(*) FILTER (WHERE score IS NOT NULL) AS scored_count,
					COUNT(*)          AS total_count
				FROM edushift.grade_records
				WHERE tenant_id = :tenantId
				  AND deleted = false
				  AND recorded_at >= :fromTs
				  AND recorded_at <  :toTs
				""")
				.setParameter("tenantId", tenantId)
				.setParameter("fromTs", from)
				.setParameter("toTs", to)
				.getSingleResult();
		double avgRaw = row[0] == null ? 0.0 : ((Number) row[0]).doubleValue();
		long scored = ((Number) row[1]).longValue();
		long total = ((Number) row[2]).longValue();
		BigDecimal normalized = total == 0
				? BigDecimal.ZERO
				: BigDecimal.valueOf(avgRaw / 20.0).setScale(6, RoundingMode.HALF_UP);

		Map<String, Object> dims = new LinkedHashMap<>();
		dims.put("avgScore", avgRaw);
		dims.put("scoredCount", scored);
		dims.put("totalCount", total);
		dims.put("periodStart", from.toString());
		dims.put("periodEnd", to.toString());
		KpiSnapshot snapshot = persistSnapshot(tenantId, MetricKey.PERFORMANCE_AVG, from, to, normalized, dims, dimsHash);
		return toKpi(snapshot);
	}

	private KpiResponse morosidadKpi(UUID tenantId, Instant from, Instant to) {
		String dimsHash = sha256("MOROSIDAD|default");
		var cached = snapshotRepository.findMostRecent(tenantId, MetricKey.MOROSIDAD, dimsHash);
		if (cached.isPresent() && isFresh(cached.get().getComputedAt())) {
			return toKpi(cached.get());
		}
		Object[] row = (Object[]) em.createNativeQuery("""
				SELECT
					COUNT(*) FILTER (WHERE status = 'OVERDUE') AS overdue_count,
					COUNT(*)                                   AS total_count
				FROM edushift.b2b_invoices
				WHERE tenant_id = :tenantId
				  AND deleted = false
				  AND issued_at >= :fromTs
				  AND issued_at <  :toTs
				""")
				.setParameter("tenantId", tenantId)
				.setParameter("fromTs", from)
				.setParameter("toTs", to)
				.getSingleResult();
		long overdue = ((Number) row[0]).longValue();
		long total = ((Number) row[1]).longValue();
		BigDecimal rate = total == 0
				? BigDecimal.ZERO
				: BigDecimal.valueOf(overdue).divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);

		Map<String, Object> dims = new LinkedHashMap<>();
		dims.put("overdueCount", overdue);
		dims.put("totalCount", total);
		dims.put("periodStart", from.toString());
		dims.put("periodEnd", to.toString());
		KpiSnapshot snapshot = persistSnapshot(tenantId, MetricKey.MOROSIDAD, from, to, rate, dims, dimsHash);
		return toKpi(snapshot);
	}

	// -----------------------------------------------------------------
	// Chart series from persisted snapshots
	// -----------------------------------------------------------------

	private ChartSeriesResponse chartFromSnapshots(UUID tenantId, MetricKey metric, Instant from, Instant to, String label) {
		List<KpiSnapshot> snapshots = snapshotRepository
				.findByTenantIdAndMetricKeyAndPeriodStartBetweenOrderByPeriodStartAsc(tenantId, metric, from, to);
		List<ChartSeriesResponse.Point> points = new ArrayList<>(snapshots.size());
		for (KpiSnapshot s : snapshots) {
			points.add(new ChartSeriesResponse.Point(s.getPeriodStart(), s.getPeriodEnd(), s.getValueNumeric()));
		}
		return new ChartSeriesResponse(label, points);
	}

	// -----------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------

	private boolean isFresh(Instant computedAt) {
		return computedAt != null
				&& computedAt.isAfter(Instant.now().minus(CACHE_FRESH_MINUTES, ChronoUnit.MINUTES));
	}

	private KpiSnapshot persistSnapshot(UUID tenantId, MetricKey metric, Instant from, Instant to,
			BigDecimal value, Map<String, Object> dims, String dimsHash) {
		if (snapshotRepository.existsByTenantIdAndMetricKeyAndPeriodStartAndPeriodEndAndDimensionsHash(
				tenantId, metric, from, to, dimsHash)) {
			log.debug("Snapshot already exists tenant={} metric={} period=[{},{}]", tenantId, metric, from, to);
			return snapshotRepository
					.findByTenantIdAndMetricKeyAndPeriodStartBetweenOrderByPeriodStartAsc(tenantId, metric, from, to)
					.stream().findFirst().orElseGet(() -> freshSnapshot(tenantId, metric, from, to, value, dims, dimsHash));
		}
		KpiSnapshot snap = freshSnapshot(tenantId, metric, from, to, value, dims, dimsHash);
		try {
			return snapshotRepository.saveAndFlush(snap);
		}
		catch (org.springframework.dao.DataIntegrityViolationException dup) {
			log.debug("Concurrent insert caught, returning existing snapshot tenant={} metric={}", tenantId, metric);
			return snapshotRepository
					.findByTenantIdAndMetricKeyAndPeriodStartBetweenOrderByPeriodStartAsc(tenantId, metric, from, to)
					.stream().findFirst().orElse(snap);
		}
	}

	private KpiSnapshot freshSnapshot(UUID tenantId, MetricKey metric, Instant from, Instant to,
			BigDecimal value, Map<String, Object> dims, String dimsHash) {
		KpiSnapshot snap = new KpiSnapshot();
		snap.setTenantId(tenantId);
		snap.setMetricKey(metric);
		snap.setPeriodStart(from);
		snap.setPeriodEnd(to);
		snap.setValueNumeric(value);
		snap.setDimensions(dims.isEmpty() ? DEFAULT_DIMENSIONS : dims);
		snap.setDimensionsHash(dimsHash);
		snap.setComputedAt(Instant.now());
		return snap;
	}

	private KpiResponse toKpi(KpiSnapshot s) {
		return new KpiResponse(s.getMetricKey().name(), s.getValueNumeric(), s.getComputedAt(), s.getDimensions());
	}

	static String sha256(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (Exception e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}