package com.edushift.modules.sessions.learning.dto;

import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One reviewed worksheet the teacher saves with a learning session.
 * {@code grade} is a single grade; another grade is another element.
 */
public record ApplicationSheetWriteRequest(

		@NotNull ApplicationSheetKind kind,

		@NotBlank
		@Size(max = 80) String grade,

		@NotBlank
		@Size(min = 5, max = 200) String title,

		@NotNull ApplicationSheetContent content
) {
}
