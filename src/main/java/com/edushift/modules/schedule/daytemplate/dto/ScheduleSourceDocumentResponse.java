package com.edushift.modules.schedule.daytemplate.dto;

import com.edushift.modules.schedule.daytemplate.entity.ScheduleParseStatus;
import com.edushift.modules.schedule.daytemplate.entity.ScheduleSourceKind;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ScheduleSourceDocumentResponse(
		UUID publicUuid,
		UUID yearUuid,
		ScheduleSourceKind kind,
		ScheduleParseStatus parseStatus,
		String originalFilename,
		String contentType,
		long fileSizeBytes,
		Map<String, Object> parsedDraftJson,
		String parseError,
		Instant createdAt
) {
}
