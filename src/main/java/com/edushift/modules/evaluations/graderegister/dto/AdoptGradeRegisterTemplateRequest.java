package com.edushift.modules.evaluations.graderegister.dto;

import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope;
import com.edushift.shared.validation.annotations.ValidUuid;
import jakarta.validation.constraints.NotNull;

public record AdoptGradeRegisterTemplateRequest(

		@NotNull(message = "academicYearPublicUuid is required")
		@ValidUuid
		String academicYearPublicUuid,

		@NotNull(message = "scope is required")
		GradeRegisterTemplateScope scope,

		@ValidUuid
		String coursePublicUuid,

		/** Required when {@code scope=LEVEL}. */
		@ValidUuid
		String levelPublicUuid,

		Boolean setAsDefault,

		/**
		 * When true: soft-delete the current working copy before adopting.
		 * TENANT replaces the year default. LEVEL replaces that level's copy.
		 * Blocked if evaluations or grade records are linked to its categories.
		 */
		Boolean replaceActive
) {
}
