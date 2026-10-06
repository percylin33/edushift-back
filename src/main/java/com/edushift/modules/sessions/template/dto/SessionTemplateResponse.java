package com.edushift.modules.sessions.template.dto;

import com.edushift.modules.sessions.template.entity.SessionTemplateFamily;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SessionTemplateResponse(
		UUID publicUuid,
		String templateKey,
		String name,
		String description,
		Map<String, Object> schema,
		boolean isSystem,
		SessionTemplateFamily family,
		UUID sourceTemplateUuid,
		boolean isDefault,
		Instant createdAt
) {
}
