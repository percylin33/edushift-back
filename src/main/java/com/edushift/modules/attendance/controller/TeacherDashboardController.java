package com.edushift.modules.attendance.controller;

import com.edushift.modules.attendance.dto.TeacherDashboardResponse;
import com.edushift.modules.attendance.service.TeacherDashboardService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the teacher attendance dashboard
 * (Sprint 9B / BE-9B.1).
 *
 * <h3>Endpoint</h3>
 * <table>
 *   <caption>Dashboard endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/dashboard/teacher</td>
 *       <td>TEACHER or TENANT_ADMIN</td>
 *       <td>{@link ApiResponse}&lt;{@link TeacherDashboardResponse}&gt;</td></tr>
 * </table>
 *
 * <h3>Why TEACHER (not just TENANT_ADMIN)</h3>
 * The FE's {@code /dashboard} page has a {@code TEACHER} branch
 * (currently rendering quick links only — see
 * {@code dashboard-home.component.ts:259-288}). Without this
 * endpoint, the teacher view has no real data — just shortcuts. The
 * self-only guarantee is enforced in the SQL layer
 * ({@code section_id in (...)} anchored to the bearer's active
 * assignments), so {@code TENANT_ADMIN} can also hit it and see the
 * teacher-scoped view if they want to.
 *
 * <h3>Latency</h3>
 * Four small aggregate queries; no per-row hydrate. The response
 * carries {@code generatedAt} so the FE can render
 * "Actualizado hace 3s" and re-poll on a 30s timer.
 */
@Slf4j
@RestController
@RequestMapping("/dashboard/teacher")
@RequiredArgsConstructor
@Tag(name = "TeacherDashboard",
		description = "TEACHER-scoped attendance snapshot: today rate, "
				+ "open/closed sessions, top absent sections (7d), recent "
				+ "closed sessions and the teacher's 'my sections today' list.")
public class TeacherDashboardController {

	private final TeacherDashboardService teacherDashboardService;

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TEACHER','TENANT_ADMIN')")
	@Operation(summary = "Teacher attendance dashboard overview",
			description = "Single-roundtrip snapshot for the bearer teacher, "
					+ "scoped to their active assignments. Cross-teacher and "
					+ "cross-tenant isolation enforced in the SQL layer.")
	public ResponseEntity<ApiResponse<TeacherDashboardResponse>> overview() {
		TeacherDashboardResponse response = teacherDashboardService.getForCurrentTeacher();
		log.debug("[attendance-api] teacher dashboard -- sectionsToday={} "
						+ "rate={} open={} absent={} topAbsent={} recentClosed={}",
				response.mySectionsToday().size(),
				response.attendanceRateToday(),
				response.openSessions(),
				response.totalAbsencesToday(),
				response.topAbsentSections().size(),
				response.recentClosedSessions().size());
		return ResponseEntity.ok(ApiResponse.ok(response));
	}
}
