package com.edushift.modules.sessions.template.dto;

import com.edushift.modules.sessions.template.entity.SessionTemplateFamily;
import java.util.List;

public record SessionTemplateGroupDto(
		SessionTemplateFamily family,
		String labelEs,
		List<SessionTemplateListItem> templates
) {
}
