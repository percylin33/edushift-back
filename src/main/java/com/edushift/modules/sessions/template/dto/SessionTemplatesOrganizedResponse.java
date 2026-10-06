package com.edushift.modules.sessions.template.dto;

import java.util.List;

public record SessionTemplatesOrganizedResponse(
		List<SessionTemplateGroupDto> groups
) {
}
