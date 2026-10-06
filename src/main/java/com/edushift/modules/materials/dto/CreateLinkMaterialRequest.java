package com.edushift.modules.materials.dto;

import com.edushift.modules.materials.entity.MaterialKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * JSON request body for a {@code kind=VIDEO_LINK} material
 * (Sprint 7a / BE-7a.1 + V105).
 */
public record CreateLinkMaterialRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 2000) String description,
		@NotNull MaterialKind kind,
		@NotBlank @Size(max = 2048) String externalUrl,
		@NotNull UUID teacherAssignmentPublicUuid
) {
}
