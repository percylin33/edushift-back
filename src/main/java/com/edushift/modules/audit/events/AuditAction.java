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
	CUSTOM

}
