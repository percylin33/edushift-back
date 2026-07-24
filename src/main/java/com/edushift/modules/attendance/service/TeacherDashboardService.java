package com.edushift.modules.attendance.service;

import com.edushift.modules.attendance.dto.TeacherDashboardResponse;

/**
 * Teacher attendance dashboard service (Sprint 9B / BE-9B.1).
 *
 * <p>Drives {@code GET /api/v1/dashboard/teacher} and returns the
 * composite snapshot the FE renders as the "Mi panel" page.</p>
 *
 * <h3>Scope contract</h3>
 * <ol>
 *   <li>Only the bearer teacher's <strong>own active assignments</strong>
 *       are considered. A teacher who teaches 3 sections sees only
 *       data from those 3 sections — never from another teacher's
 *       sections. The SQL is anchored to
 *       {@code section_id in (select section_id from teacher_assignments
 *       where teacher_id = ? and unassignedAt is null)}.</li>
 *   <li>All times are UTC. ADR-6.12 (per-tenant timezone) is
 *       deferred to a follow-up.</li>
 *   <li>Read-only and idempotent: no DB writes.</li>
 *   <li>Defensive against empty data: a teacher with no active
 *       assignments returns {@link TeacherDashboardResponse#empty()},
 *       not a {@code NullPointerException}.</li>
 * </ol>
 */
public interface TeacherDashboardService {

	/**
	 * @return full snapshot for the bearer teacher, scoped to their
	 *         active assignments. Empty snapshot if the teacher has
	 *         none (e.g. substitute teacher with no current courses).
	 */
	TeacherDashboardResponse getForCurrentTeacher();
}
