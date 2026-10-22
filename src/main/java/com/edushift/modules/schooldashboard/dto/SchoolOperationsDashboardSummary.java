package com.edushift.modules.schooldashboard.dto;

import java.time.Instant;

/** Aggregate operational indicators for the authenticated school tenant. */
public record SchoolOperationsDashboardSummary(
		String academicYear,
		boolean annualDisclosurePackageExists,
		long disclosuresPendingPublication,
		long disclosuresPublished,
		long admissionsInProcess,
	long admissionsWaitlisted,
		long admissionsAdmitted,
		long admissionsEnrolled,
		long evaluationsTotal,
		long evaluationsDraft,
		long evaluationsPublished,
		long evaluationsClosed,
		Instant generatedAt) {

	public static SchoolOperationsDashboardSummary empty(Instant generatedAt) {
		return new SchoolOperationsDashboardSummary(
				null, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, generatedAt);
	}
}
