package com.edushift.modules.audit.events;

/**
 * Canonical audit actions. Modules may use the generic CRUD ones or
 * domain-specific ones (LOGIN, EXPORT, etc.). Extend as needed.
 */
public enum AuditAction {

	CREATE,
	READ,
	UPDATE,
	DELETE,
	LOGIN,
	LOGOUT,
	LOGIN_FAILED,
	ACCESS_DENIED,
	EXPORT,
	IMPORT,
	IMPERSONATE,
	PERMISSION_GRANTED,
	PERMISSION_REVOKED,
	CONFIG_CHANGED,
	JOB_EXECUTED,
	/** Sprint 14 (MVP Closure) / DEBT-AUTH-4: refresh-token mass revocation triggered by user status change. */
	ADMIN_REVOKE,
	/** Sprint 15 / DEBT-BE-F-02: SUPER_ADMIN credential rotation via dev-only break-glass. */
	ADMIN_PASSWORD_RESET,
	ADMIN_PASSWORD_RESET_FAILED,
	CUSTOM

}
