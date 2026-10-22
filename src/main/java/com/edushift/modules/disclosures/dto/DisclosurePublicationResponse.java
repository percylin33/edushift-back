package com.edushift.modules.disclosures.dto;

import java.time.Instant;
import java.util.UUID;

public record DisclosurePublicationResponse(
		UUID publicUuid,
		UUID academicYearPublicUuid,
		String academicYearName,
		int versionNumber,
		DisclosureContent content,
		String contentSha256,
		Instant approvedAt,
		Instant publishedAt
) { }
