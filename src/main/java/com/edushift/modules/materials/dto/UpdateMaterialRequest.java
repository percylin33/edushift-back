package com.edushift.modules.materials.dto;

import com.edushift.modules.materials.entity.MaterialKind;
import jakarta.validation.constraints.Size;

/**
 * PATCH body for a material (Sprint 7a / BE-7a.1). All fields are
 * optional but at least one must be present (the service raises
 * {@code RECORD_EMPTY_PATCH} when none are).
 */
public record UpdateMaterialRequest(
		@Size(max = 200) String title,
		@Size(max = 2000) String description,
		MaterialKind kind,
		@Size(max = 2048) String externalUrl
) {
}
