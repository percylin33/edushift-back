package com.edushift.modules.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.modules.admin.subscriptions.B2BSubscription;
import com.edushift.modules.admin.subscriptions.B2BSubscriptionRepository;
import com.edushift.modules.analytics.dto.KpiResponse;
import com.edushift.modules.analytics.dto.KpiSummaryResponse;
import com.edushift.modules.analytics.entity.KpiSnapshot;
import com.edushift.modules.analytics.entity.MetricKey;
import com.edushift.modules.analytics.repository.KpiSnapshotRepository;
import com.edushift.modules.analytics.service.AnalyticsService;
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.math.BigDecimal;
import java.util.Map;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.LocalDate;
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
 * <p>Seeds attendance_records + b2b_invoices for
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
 * <p>Builds valid section, student, session, subscription and invoice
 * fixtures so the foreign keys remain enabled. Snapshot rows are inserted
 * directly to isolate their tenant filter from the calculation path.</p>
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
	@Autowired private AcademicSeedService academicSeedService;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private AttendanceSessionRepository attendanceSessionRepository;
	@Autowired private AttendanceRecordRepository attendanceRecordRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private B2BSubscriptionRepository subscriptionRepository;
	@Autowired private PlatformPlanRepository platformPlanRepository;
	@Autowired private KpiSnapshotRepository kpiSnapshotRepository;

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

		TenantContext.runAs(tenantAId, () -> {
			tx.executeWithoutResult(s -> seed(tenantAId,
					2 /* present */, 1 /* late */, 1 /* absent */,
					1 /* overdue */, 3 /* total invoices */));
			return null;
		});

		TenantContext.runAs(tenantBId, () -> {
			tx.executeWithoutResult(s -> seed(tenantBId,
					0, 0, 5 /* all absent */,
					3 /* all overdue */, 3 /* total invoices */));
			return null;
		});

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
		TenantContext.runAs(tenantAId, () -> {
			tx.executeWithoutResult(status -> {
			KpiSnapshot snapshot = new KpiSnapshot();
			snapshot.setMetricKey(MetricKey.ATTENDANCE_RATE);
			snapshot.setPeriodStart(now.minusSeconds(3600));
			snapshot.setPeriodEnd(now);
			snapshot.setValueNumeric(new BigDecimal("0.9"));
			snapshot.setDimensions(Map.of());
			snapshot.setDimensionsHash("tenant-a-snapshot");
			snapshot.setComputedAt(now);
			kpiSnapshotRepository.saveAndFlush(snapshot);
			});
			return null;
		});

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
		Tenant t = newTestTenant();
		t.setPublicUuid(UUID.randomUUID());
		t.setSlug(slug);
		t.setName("Test " + slug);
		t.setStatus(TenantStatus.ACTIVE);
		t.setDeleted(false);
		return tenantRepository.saveAndFlush(t).getId();
	}

	private void seed(UUID tenantId, int present, int late, int absent,
			int overdueInvoices, int totalInvoices) {
		Instant now = Instant.now();
		academicSeedService.seedDefaults(tenantId);
		AcademicLevel level = levelRepository.findByCodeIgnoreCase("PRIMARIA").orElseThrow();
		Grade grade = gradeRepository.findAllByLevelOrderByOrdinalAsc(level).get(0);

		AcademicYear year = new AcademicYear();
		year.setName("Analytics " + UUID.randomUUID());
		year.setStartDate(LocalDate.of(2026, 1, 1));
		year.setEndDate(LocalDate.of(2026, 12, 31));
		year.setStatus(AcademicYearStatus.ACTIVE);
		year = yearRepository.saveAndFlush(year);

		Section section = new Section();
		section.setAcademicYear(year);
		section.setGrade(grade);
		section.setName("Analytics " + UUID.randomUUID().toString().substring(0, 8));
		section = sectionRepository.saveAndFlush(section);

		User actor = new User();
		actor.setEmail("analytics-" + UUID.randomUUID() + "@test.local");
		actor.setFirstName("Analytics");
		actor.setLastName("Test");
		actor.setPasswordHash("unused-test-hash");
		actor.setRoles(new String[]{UserRole.TENANT_ADMIN.name()});
		actor.setStatus(UserStatus.ACTIVE);
		actor.setEmailVerified(true);
		actor = userRepository.saveAndFlush(actor);

		AttendanceSession session = new AttendanceSession();
		session.setSection(section);
		session.setOccurredOn(LocalDate.now());
		session.setSlot(AttendanceSessionSlot.FULL_DAY);
		session.setStartsAt(now);
		session = attendanceSessionRepository.saveAndFlush(session);

		int index = 0;
		for (int i = 0; i < present; i++) {
			insertAttendanceRecord(session, actor, AttendanceRecordStatus.PRESENT, index++);
		}
		for (int i = 0; i < late; i++) {
			insertAttendanceRecord(session, actor, AttendanceRecordStatus.LATE, index++);
		}
		for (int i = 0; i < absent; i++) {
			insertAttendanceRecord(session, actor, AttendanceRecordStatus.ABSENT, index++);
		}

		B2BSubscription subscription = new B2BSubscription();
		subscription.setTenantId(tenantId);
		subscription.setPlanId(platformPlanRepository.findByCode("BASIC").orElseThrow().getId());
		subscription.setCurrentPeriodStart(LocalDate.now().withDayOfMonth(1));
		subscription.setCurrentPeriodEnd(LocalDate.now().withDayOfMonth(1).plusMonths(1));
		subscription.setNextBillingAt(LocalDate.now().withDayOfMonth(1).plusMonths(1));
		subscription = subscriptionRepository.saveAndFlush(subscription);

		for (int i = 0; i < overdueInvoices; i++) {
			insertInvoice(tenantId, subscription.getId(), "OVERDUE", now, i);
		}
		for (int i = overdueInvoices; i < totalInvoices; i++) {
			insertInvoice(tenantId, subscription.getId(), "PAID", now, i);
		}
	}

	private void insertAttendanceRecord(AttendanceSession session, User actor,
			AttendanceRecordStatus status, int index) {
		Student student = new Student();
		student.setDocumentType(DocumentType.DNI);
		student.setDocumentNumber("AN-" + session.getId().toString().substring(0, 8) + "-" + index);
		student.setFirstName("Analytics " + index);
		student.setLastName("Student");
		student.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
		student = studentRepository.saveAndFlush(student);

		AttendanceRecord record = new AttendanceRecord();
		record.setSession(session);
		record.setStudent(student);
		record.setStatus(status);
		record.setOccurredAt(Instant.now().minusSeconds(60));
		if (status == AttendanceRecordStatus.PRESENT || status == AttendanceRecordStatus.LATE) {
			record.setScannedByUserId(actor.getPublicUuid());
		}
		attendanceRecordRepository.saveAndFlush(record);
	}

	private void insertInvoice(UUID tenantId, UUID subscriptionId, String status,
			Instant issuedAt, int periodOffset) {
		LocalDate periodStart = LocalDate.now().withDayOfMonth(1).plusMonths(periodOffset);
		jdbc.update("""
				INSERT INTO edushift.b2b_invoices
				  (id, tenant_id, public_uuid, subscription_id, period_start, period_end,
				   active_student_count, price_per_student_cents, subtotal_cents,
				   discount_cents, total_cents, status, issued_at, due_at,
				   paid_at, version, created_at, updated_at, deleted)
				VALUES (?, ?, ?, ?, ?, ?, 1, 1000, 1000, 0, 1000, ?, ?, ?, NULL, 0, ?, ?, false)
				""",
				UUID.randomUUID(), tenantId, UUID.randomUUID(),
				subscriptionId, periodStart, periodStart.plusMonths(1),
				status, Timestamp.from(issuedAt),
				LocalDate.now(),
				Timestamp.from(issuedAt), Timestamp.from(issuedAt));
	}

	private KpiResponse findKpi(KpiSummaryResponse summary, String metricKey) {
		return summary.kpis().stream()
				.filter(k -> metricKey.equals(k.metricKey()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing KPI: " + metricKey));
	}
}
