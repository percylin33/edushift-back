package com.edushift.modules.sessions.template.dto;

import com.edushift.modules.sessions.template.entity.SessionTemplateFamily;
import java.util.List;
import java.util.UUID;

public record SessionTemplateListItem(
		UUID publicUuid,
		String templateKey,
		String name,
		String description,
		SessionTemplateFamily family,
		String familyLabel,
		String origin,
		boolean isSystem,
		boolean isDefault,
		boolean adopted,
		boolean canAdopt,
		boolean canEdit,
		List<SessionTemplatePhasePreview> phasesPreview
) {
}
