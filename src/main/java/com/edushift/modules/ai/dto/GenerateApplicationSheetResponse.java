package com.edushift.modules.ai.dto;

import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import java.util.UUID;

public record GenerateApplicationSheetResponse(
		ApplicationSheetKind kind,
		String grade,
		String title,
		ApplicationSheetContent content,
		String model,
		String provider,
		String promptVersion,
		UUID generationUuid
) {
}
