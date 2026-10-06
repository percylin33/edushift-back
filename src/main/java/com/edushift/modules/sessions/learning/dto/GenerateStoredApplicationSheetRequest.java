package com.edushift.modules.sessions.learning.dto;

import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Asks for one new sheet of an existing session, for one grade. */
public record GenerateStoredApplicationSheetRequest(

		@NotNull ApplicationSheetKind kind,

		@NotBlank
		@Size(max = 80) String grade
) {
}
