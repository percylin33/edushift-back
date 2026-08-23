package com.edushift.modules.schedule.daytemplate.entity;

/**
 * Parse lifecycle of a {@link ScheduleSourceDocument}.
 */
public enum ScheduleParseStatus {
	UPLOADED,
	PARSED,
	COMMITTED,
	FAILED,
	/** PDF / image kept as visual reference only (no OCR in v1). */
	REFERENCE_ONLY
}
