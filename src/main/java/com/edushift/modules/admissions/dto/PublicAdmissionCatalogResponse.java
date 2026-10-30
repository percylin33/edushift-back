package com.edushift.modules.admissions.dto;

import java.util.List;
import java.util.UUID;

public record PublicAdmissionCatalogResponse(
		String tenantName,
		List<YearOption> years,
		List<GradeOption> grades
) {
	public record YearOption(UUID publicUuid, String name) {}
	public record GradeOption(UUID publicUuid, String name, String levelName) {}
}
