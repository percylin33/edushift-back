package com.edushift.modules.attendance.service;

import com.edushift.modules.attendance.dto.DashboardOverviewResponse;

/**
 * Admin attendance dashboard service (Sprint 6 / BE-6.5).
 *
 * <p>Drives {@code GET /api/v1/attendance/dashboard/overview} and
 * returns the composite snapshot the FE renders as the "Asistencia
 * del dia" page.
 *
 * <h3>Implementation contract</h3>
 * <ol>
 *   <li>All four today KPIs are computed against the caller's
 *       <strong>current</strong> tenant — the tenant context is
 *       pulled from {@code CurrentUserProvider}, never from a
 *       client-supplied parameter.</li>
 *   <li>"Today" is the UTC calendar date at the moment the call
 *       lands. ADR-6.12 tracks the per-tenant timezone follow-up.</li>
 *   <li>Read-only and idempotent: no DB writes.</li>
 *   <li>Defensive against empty data: a brand-new tenant with no
 *       sessions today returns a snapshot of zeros + empty lists,
 *       never a {@code NullPointerException}.</li>
 * </ol>
 *
 * <h3>Performance</h3>
 * Five small aggregate queries; no per-session hydrate. At our
 * scale (Sprint 9 target ~30k sessions/tenant) this is comfortably
 * sub-100ms in p99 on a warm Postgres connection. Caching can be
 * added behind the same interface once we have a real number to
 * optimise against (deferred to a follow-up sprint).
 */
public interface DashboardService {

	/**
	 * @return full snapshot for the current tenant.
	 */
	DashboardOverviewResponse getOverview();
}
