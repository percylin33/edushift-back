package com.edushift.shared.security;

/**
 * Granular LMS authorities (Sprint 7a / BE-7a.3).
 *
 * <p>These constants are the source of truth for the
 * {@code LMS_*} authorities consumed by
 * {@code @PreAuthorize("hasAuthority('LMS_TASK_READ')")} in the
 * controllers. The mapping from a coarse user role
 * ({@code TENANT_ADMIN}, {@code TEACHER}, {@code STUDENT},
 * {@code PARENT}, {@code STAFF}) to a set of these authorities is
 * centralised in {@link LmsRoleAuthorityMapper} and applied by
 * {@code JwtAuthenticationFilter} at authentication time.
 *
 * <p>Naming convention: {@code LMS_<noun>_<verb>}. The verb
 * follows the standard CRUD-ish set: {@code READ}, {@code CREATE},
 * {@code UPDATE}, {@code DELETE}, plus the LMS-specific
 * {@code SUBMIT} (student/parent) and {@code GRADE} (teacher).
 *
 * <p>Closes part of {@code DEBT-SEC-1} for the LMS flow.
 */
public final class LmsAuthorities {

	// Tasks
	public static final String LMS_TASK_READ = "LMS_TASK_READ";
	public static final String LMS_TASK_CREATE = "LMS_TASK_CREATE";
	public static final String LMS_TASK_GRADE = "LMS_TASK_GRADE";
	public static final String LMS_TASK_SUBMIT = "LMS_TASK_SUBMIT";

	// Materials
	public static final String LMS_MATERIAL_READ = "LMS_MATERIAL_READ";
	public static final String LMS_MATERIAL_WRITE = "LMS_MATERIAL_WRITE";
	public static final String LMS_MATERIAL_DELETE = "LMS_MATERIAL_DELETE";

	// Quizzes (Sprint 7b, BE-7b.0). Mirrored in
	// edushift-front/.../core/enums/permission.enum.ts (LmsQuizRead/Create/Grade/Submit).
	// Sprint 7b is currently a placeholder (see sprint-07b-lms-intelligence.md);
	// these constants exist so the FE-7b.0 RBAC wiring is stable and the
	// upcoming BE-7b.1 controllers can be gated end-to-end.
	public static final String LMS_QUIZ_READ = "LMS_QUIZ_READ";
	public static final String LMS_QUIZ_CREATE = "LMS_QUIZ_CREATE";
	public static final String LMS_QUIZ_GRADE = "LMS_QUIZ_GRADE";
	public static final String LMS_QUIZ_SUBMIT = "LMS_QUIZ_SUBMIT";

	// AI assistant (Sprint 7c, BE-7c.1). Separate from LMS_QUIZ_CREATE so
	// tenants can disable AI generation without affecting the quiz builder.
	// Mirrored in edushift-front/.../core/enums/permission.enum.ts
	// (Permission.AiUse).
	public static final String LMS_AI_GENERATE = "LMS_AI_GENERATE";

	// Payments admin (Sprint 11 / BE-11.7). Gates the
	// /api/v1/admin/payments/* endpoints (reconcile, refund, mark-paid-cash).
	// Granted to TENANT_ADMIN and STAFF (front-desk cashiers). Mirrored in
	// edushift-front/.../core/enums/permission.enum.ts (Permission.PaymentAdmin).
	public static final String LMS_PAYMENT_ADMIN = "LMS_PAYMENT_ADMIN";

	// Announcements (Sprint 9 / BE-9.4). Gates the admin surface of
	// /api/v1/announcements (create / patch / publish / delete). Granted to
	// TENANT_ADMIN (school directors / admins) and TEACHER (homeroom
	// teachers post class-level announcements). Mirrored in
	// edushift-front/.../core/enums/permission.enum.ts.
	// DEBT-FK-BUGS-2: this constant was missing — AnnouncementController
	// referenced the string literal directly, which silently always
	// returned 403 regardless of the role. Centralised here so the
	// @PreAuthorize can resolve it via hasAuthority(...).
	public static final String LMS_ANNOUNCEMENTS_CREATE = "LMS_ANNOUNCEMENTS_CREATE";

	// Notifications admin (Sprint 10 / BE-10.x). Gates the admin surface
	// of /api/v1/notifications/templates and /api/v1/notifications/admin/*
	// (create / publish / configure). Granted to TENANT_ADMIN + SUPER_ADMIN.
	// DEBT-QA-2 (GAP-2 from QA plan 2026-07-02): this constant was missing
	// although NotificationController.java:132,138 referenced the string
	// literal directly, which silently always returned 403 for every role.
	// Centralised here so @PreAuthorize resolves it via hasAuthority(...).
	public static final String LMS_NOTIFICATIONS_MANAGE = "LMS_NOTIFICATIONS_MANAGE";

	// AI usage visibility (Sprint 7c / BE-7c.2). Gates GET /api/v1/ai/usage
	// (own-tenant AI token consumption breakdown, quotas, billing). Granted
	// to TENANT_ADMIN + SUPER_ADMIN.
	// DEBT-QA-2 (GAP-2 from QA plan 2026-07-02): missing — UsageController
	// referenced the literal silently and always returned 403.
	public static final String LMS_AI_USAGE = "LMS_AI_USAGE";

	private LmsAuthorities() {
		// utility class
	}
}
