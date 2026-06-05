package com.edushift.modules.students.entity;

/**
 * Kind of resource a bulk-import job operates on.
 *
 * <p>The enum is currently single-valued (only students can be bulk-imported
 * in Sprint 3) but the column ({@code bulk_import_jobs.job_type}) and the
 * machinery around it are generic so future imports — guardians, classes,
 * enrolments — can plug in without a schema migration.
 */
public enum BulkImportJobType {
	STUDENTS
}
