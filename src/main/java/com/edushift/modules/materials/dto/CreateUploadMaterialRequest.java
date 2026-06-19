package com.edushift.modules.materials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Multipart upload request for a {@code kind=FILE} material
 * (Sprint 7a / BE-7a.1). The actual binary is bound as a separate
 * {@code @RequestPart MultipartFile file} on the controller method;
 * this record carries the metadata only.
 *
 * <p>Constraints mirror the DB columns: {@code title varchar(200)}
 * and {@code description varchar(2000)}.
 */
public record CreateUploadMaterialRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 2000) String description
) {
}
