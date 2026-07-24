package com.edushift.modules.students.entity;

/**
 * Kind of resource a bulk-import job operates on.
 *
 * <p>The enum whitelists which bulk-import targets are supported. The
 * underlying {@code bulk_import_jobs.job_type} column has a CHECK
 * constraint that must stay in sync with this enum (see V83 +
 * {@code chk_bulk_import_jobs_job_type}). When adding a new value,
 * append the constant here AND extend the CHECK whitelist in a new
 * migration.</p>
 */
public enum BulkImportJobType {
	STUDENTS,
	/** Cierre-B / F7 — bulk-import teachers from .xlsx. */
	TEACHERS
}
