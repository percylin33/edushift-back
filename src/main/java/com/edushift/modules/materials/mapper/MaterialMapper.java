package com.edushift.modules.materials.mapper;

import com.edushift.modules.files.dto.FileObjectResponse;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.materials.dto.MaterialResponse;
import com.edushift.modules.materials.dto.MaterialSummary;
import com.edushift.modules.materials.entity.Material;
import com.edushift.modules.materials.entity.MaterialKind;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Material} (Sprint 7a / BE-7a.1).
 *
 * <p>The mapper never persists anything; it only translates between
 * the entity and its DTO projections. URL building (the
 * {@code file.url} field) is the controller's job because it needs
 * the request's tenant context to build a stable download URL.
 */
@Component
public class MaterialMapper {

	/**
	 * Build a {@link MaterialResponse} from the entity. The caller
	 * must supply the resolved {@link FileObjectResponse} (with
	 * {@code url} populated) for {@code kind=FILE}; pass {@code null}
	 * for {@code kind=VIDEO_LINK}.
	 */
	public MaterialResponse toResponse(Material entity, FileObjectResponse fileResponse) {
		String externalUrl = entity.getKind() == MaterialKind.VIDEO_LINK
				? entity.getExternalUrl()
				: null;
		return new MaterialResponse(
				entity.getPublicUuid(),
				entity.getSection() != null ? entity.getSection().getPublicUuid() : null,
				entity.getTitle(),
				entity.getDescription(),
				entity.getKind(),
				fileResponse,
				externalUrl,
				entity.getOwnerUserId(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	public MaterialSummary toSummary(Material entity) {
		// fileSizeBytes is not populated here: the list endpoint
		// avoids a JOIN on lms_file_objects for performance. A future
		// "list with sizes" DTO (DEBT-7A-10) will provide it via a
		// dedicated repository query. The single-material GET still
		// surfaces the size through MaterialResponse.file.
		return new MaterialSummary(
				entity.getPublicUuid(),
				entity.getTitle(),
				entity.getKind(),
				null,
				entity.getOwnerUserId(),
				entity.getCreatedAt());
	}

	public List<MaterialSummary> toSummaryList(List<Material> entities) {
		return entities.stream().map(this::toSummary).toList();
	}

	/**
	 * Helper to build a {@link FileObjectResponse} envelope from a
	 * {@link FileObject} with the supplied URL. Kept here so the
	 * controller does not need to import the files DTO directly.
	 */
	public FileObjectResponse fileResponse(FileObject entity, String url) {
		return FileObjectResponse.fromEntity(entity, url);
	}
}
