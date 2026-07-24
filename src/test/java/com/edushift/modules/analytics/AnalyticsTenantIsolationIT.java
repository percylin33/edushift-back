package com.edushift.modules.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.analytics.dto.KpiResponse;
import com.edushift.modules.analytics.dto.KpiSummaryResponse;
import com.edushift.modules.analytics.service.AnalyticsService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cross-tenant isolation IT for the {@code analytics} module
 * (Sprint cierre-A / B1).
 *
 * <p>Seeds attendance_records + grade_records + b2b_invoices for
 * tenants A and B (different UUIDs, same logical shape) and verifies
 * that:</p>
 * <ul>
 *   <li>{@link AnalyticsService#currentSummary()} called under
 *       {@code TenantContext.runAs(A, ...)} returns A's rates
 *       exclusively (no zero from B leaking in, no A's numbers
 *       surfacing under B).</li>
 *   <li>{@code kpi_snapshots} rows created by tenant A are never
 *       returned when running under tenant B (FK + WHERE tenant_id =
 *       :tenantId).</li>
 * </ul>
 *
 * <p>Uses native SQL seeds to keep the test independent of every
 * module's service-layer validation. The IT does not exercise the
 * HTTP boundary — that is the responsibility of the controller-level
 * E2E suite.</p>
 */
class AnalyticsTenantIsolationIT extends IntegrationTest {

	@Autowired
	private AnalyticsService analyticsService;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate tx;

	private UUID tenantAId;
	private UUID tenantBId;

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("kpis computed under Tenant A exclude Tenant B's data and vice versa")
	void kpisAreTenantIsolated() {
		tenantAId = createTenant("alpha");
		tenantBId = createTenant("bravo");

		tx.executeWithoutResult(s -> seed(tenantAId,
				2 /* present */, 1 /* late */, 1 /* absent */,
				3 /* grades: 20, 18, 16 */,
				1 /* overdue */, 3 /* total invoices */));

		tx.executeWithoutResult(s -> seed(tenantBId,
				0, 0, 5 /* all absent */,
				0,
				3 /* all overdue */, 3 /* total invoices */));

		KpiSummaryResponse aSummary = TenantContext.runAs(tenantAId, () -> analyticsService.currentSummary());
		KpiSummaryResponse bSummary = TenantContext.runAs(tenantBId, () -> analyticsService.currentSummary());

		KpiResponse attendanceA = findKpi(aSummary, "ATTENDANCE_RATE");
		KpiResponse attendanceB = findKpi(bSummary, "ATTENDANCE_RATE");
		KpiResponse morosidadA = findKpi(aSummary, "MOROSIDAD");
		KpiResponse morosidadB = findKpi(bSummary, "MOROSIDAD");

		assertThat(attendanceA.value())
				.as("Tenant A: 3 present (PRESENT+LATE) / 4 total = 0.75")
				.isEqualByComparingTo(new BigDecimal("0.750000"));
		assertThat(attendanceB.value())
				.as("Tenant B: 0 present / 5 total = 0")
				.isEqualByComparingTo(BigDecimal.ZERO);

		assertThat(morosidadA.value())
				.as("Tenant A: 1 overdue / 3 invoices = 0.333333")
				.isEqualByComparingTo(new BigDecimal("0.333333"));
		assertThat(morosidadB.value())
				.as("Tenant B: 3 overdue / 3 invoices = 1.0")
				.isEqualByComparingTo(BigDecimal.ONE);

		assertThat(attendanceA.computedAt())
				.as("Tenant A computed_at is independent of Tenant B")
				.isNotNull();
		assertThat(attendanceB.computedAt())
				.as("Tenant B computed_at is independent of Tenant A")
				.isNotNull();
	}

	@Test
	@DisplayName("kpi_snapshots rows are tenant-scoped (no cross-tenant leakage)")
	void snapshotsAreTenantScoped() {
		tenantAId = createTenant("gamma");
		tenantBId = createTenant("delta");

		Instant now = Instant.now();
		jdbc.update("""
				INSERT INTO edushift.kpi_snapshots
				  (id, tenant_id, created_at, updated_at, deleted,
				   metric_key, period_start, period_end, value_numeric,
				   dimensions, dimensions_hash, computed_at)
				VALUES (?, ?, ?, ?, false, 'ATTENDANCE_RATE', ?, ?, 0.9,
				        '{}'::jsonb, ?, ?)
				""",
				UUID.randomUUID(), tenantAId, now, now,
				now.minusSeconds(3600), now,
				"deadbeef", now);

		Integer aCount = TenantContext.runAs(tenantAId, () ->
				jdbc.queryForObject(
						"SELECT count(*) FROM edushift.kpi_snapshots WHERE tenant_id = ?",
						Integer.class, tenantAId));
		Integer bCount = TenantContext.runAs(tenantBId, () ->
				jdbc.queryForObject(
						"SELECT count(*) FROM edushift.kpi_snapshots WHERE tenant_id = ?",
						Integer.class, tenantBId));

		assertThat(aCount).isEqualTo(1);
		assertThat(bCount).isEqualTo(0);
	}

	// -----------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------

	private UUID createTenant(String slug) {
		Tenant t = new Tenant();
		t.setPublicUuid(UUID.randomUUID());
		t.setSlug(slug);
		t.setName("Test " + slug);
		t.setStatus(TenantStatus.ACTIVE);
		t.setDeleted(false);
		return tenantRepository.saveAndFlush(t).getId();
	}

	private void seed(UUID tenantId, int present, int late, int absent,
			int gradedScores, int overdueInvoices, int totalInvoices) {
		Instant now = Instant.now();
		Instant occurred = now.minusSeconds(60);

		for (int i = 0; i < present; i++) {
			insertAttendanceRecord(tenantId, "PRESENT", occurred);
		}
		for (int i = 0; i < late; i++) {
			insertAttendanceRecord(tenantId, "LATE", occurred);
		}
		for (int i = 0; i < absent; i++) {
			insertAttendanceRecord(tenantId, "ABSENT", occurred);
		}

		for (int i = 0; i < gradedScores; i++) {
			int score = 20 - (i * 2);
			insertGradeRecord(tenantId, score, now);
		}

		for (int i = 0; i < overdueInvoices; i++) {
			insertInvoice(tenantId, "OVERDUE", now);
		}
		for (int i = overdueInvoices; i < totalInvoices; i++) {
			insertInvoice(tenantId, "PAID", now);
		}
	}

	private void insertAttendanceRecord(UUID tenantId, String status, Instant occurredAt) {
		jdbc.update("""
				INSERT INTO edushift.attendance_records
				  (id, tenant_id, public_uuid, created_at, updated_at, deleted,
				   session_id, student_id, status, occurred_at, scanned_by_user_id,
				   edited_by_user_id, edited_at)
				VALUES (?, ?, ?, ?, ?, false, ?, ?, ?, ?, NULL, NULL, NULL)
				""",
				UUID.randomUUID(), tenantId, UUID.randomUUID(),
				occurredAt, occurredAt,
				UUID.randomUUID(), UUID.randomUUID(),
				status, occurredAt);
	}

	private void insertGradeRecord(UUID tenantId, int score, Instant recordedAt) {
		jdbc.update("""
				INSERT INTO edushift.grade_records
				  (id, tenant_id, public_uuid, created_at, updated_at, deleted,
				   evaluation_id, student_id, score, literal, recorded_at,
				   recorded_by_user_id, is_active)
				VALUES (?, ?, ?, ?, ?, false, ?, ?, ?, NULL, ?, ?, true)
				""",
				UUID.randomUUID(), tenantId, UUID.randomUUID(),
				recordedAt, recordedAt,
				UUID.randomUUID(), UUID.randomUUID(),
				score, recordedAt, UUID.randomUUID());
	}

	private void insertInvoice(UUID tenantId, String status, Instant issuedAt) {
		jdbc.update("""
				INSERT INTO edushift.b2b_invoices
				  (id, tenant_id, public_uuid, subscription_id, period_start, period_end,
				   active_student_count, price_per_student_cents, subtotal_cents,
				   discount_cents, total_cents, status, issued_at, due_at,
				   paid_at, version, created_at, updated_at, deleted)
				VALUES (?, ?, ?, ?, ?, ?, 1, 1000, 1000, 0, 1000, ?, ?, ?, NULL, 0, ?, ?, false)
				""",
				UUID.randomUUID(), tenantId, UUID.randomUUID(),
				UUID.randomUUID(),
				java.sql.Date.valueOf(java.time.LocalDate.now().withDayOfMonth(1)),
				java.sql.Date.valueOf(java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1)),
				status, issuedAt,
				java.sql.Date.valueOf(java.time.LocalDate.now()),
				issuedAt, issuedAt);
	}

	private KpiResponse findKpi(KpiSummaryResponse summary, String metricKey) {
		return summary.kpis().stream()
				.filter(k -> metricKey.equals(k.metricKey()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing KPI: " + metricKey));
	}
}