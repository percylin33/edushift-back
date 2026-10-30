package com.edushift.modules.disclosures.dto;

import com.edushift.modules.disclosures.entity.DisclosureStatus;
import java.time.Instant;
import java.util.UUID;

public record DisclosurePackageResponse(
		UUID publicUuid,
		UUID academicYearPublicUuid,
		String academicYearName,
		DisclosureStatus status,
		int revisionNumber,
		Integer latestPublishedVersion,
		DisclosureContent content,
		DisclosureCompleteness completeness,
		String reviewNote,
		Instant submittedAt,
		UUID submittedBy,
		Instant approvedAt,
		UUID approvedBy,
		Instant publishedAt,
		UUID publishedBy
) { }
