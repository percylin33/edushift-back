package com.edushift.modules.admissions.entity;

/** Administrative lifecycle of one application; enrollment remains a separate record. */
public enum AdmissionApplicationStatus {
	RECEIVED,
	UNDER_REVIEW,
	ADMITTED,
	WAITLISTED,
	REJECTED,
	WITHDRAWN,
	ENROLLED
}
