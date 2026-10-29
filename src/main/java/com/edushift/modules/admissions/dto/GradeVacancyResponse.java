package com.edushift.modules.admissions.dto;

import java.util.UUID;

public record GradeVacancyResponse(
		UUID gradePublicUuid,
		String gradeName,
		int sectionCount,
		int configuredCapacity,
		long activeEnrollments,
		long admittedApplications,
		long temporaryReservations,
		int availableSeats,
		boolean capacityConfigured
) {
	public GradeVacancyResponse(UUID gradePublicUuid, String gradeName, int sectionCount, int configuredCapacity,
			long activeEnrollments, long admittedApplications, int availableSeats, boolean capacityConfigured) {
		this(gradePublicUuid, gradeName, sectionCount, configuredCapacity, activeEnrollments,
				admittedApplications, 0, availableSeats, capacityConfigured);
	}
}
