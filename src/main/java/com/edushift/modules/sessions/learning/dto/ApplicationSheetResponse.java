package com.edushift.modules.sessions.learning.dto;

import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import java.util.UUID;

public record ApplicationSheetResponse(
		UUID publicUuid,
		ApplicationSheetKind kind,
		String grade,
		int sheetNumber,
		String title,
		ApplicationSheetContent content
) {
}
