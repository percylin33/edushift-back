package com.edushift.modules.family.dto;

import java.time.Instant;
import java.util.UUID;

public record FamilyActivityItemDto(
		String kind,
		UUID publicUuid,
		String title,
		String sectionName,
		Instant dueAt,
		boolean overdue,
		String status
) {
}
