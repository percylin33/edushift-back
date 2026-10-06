package com.edushift.modules.materials.mapper;

import com.edushift.modules.files.dto.FileObjectResponse;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.materials.dto.MaterialResponse;
import com.edushift.modules.materials.dto.MaterialSummary;
import com.edushift.modules.materials.entity.Material;
import com.edushift.modules.materials.entity.MaterialKind;
import com.edushift.modules.teachers.assignments.support.LmsAssignmentProjection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Material} (Sprint 7a / BE-7a.1 + V105).
 */
@Component
@RequiredArgsConstructor
public class MaterialMapper {

	private final FileObjectService fileObjectService;

	public MaterialResponse toResponse(Material entity, FileObjectResponse fileResponse) {
		String externalUrl = entity.getKind() == MaterialKind.VIDEO_LINK
				? entity.getExternalUrl()
				: null;
		var assignment = entity.getTeacherAssignment();
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
				entity.getUpdatedAt(),
				LmsAssignmentProjection.publicUuid(assignment),
				LmsAssignmentProjection.coursePublicUuid(assignment),
				LmsAssignmentProjection.courseName(assignment),
				LmsAssignmentProjection.periodLabel(assignment));
	}

	public MaterialSummary toSummary(Material entity) {
		var assignment = entity.getTeacherAssignment();
		Long fileSizeBytes = null;
		String filename = null;
		String contentType = null;
		UUID filePublicUuid = entity.getFilePublicUuid();
		if (filePublicUuid != null) {
			var fileOpt = fileObjectService.findByPublicUuid(filePublicUuid);
			if (fileOpt.isPresent()) {
				FileObject fo = fileOpt.get();
				fileSizeBytes = fo.getSizeBytes();
				filename = fo.getOriginalName();
				contentType = fo.getContentType();
			}
		}
		String externalUrl = entity.getKind() == MaterialKind.VIDEO_LINK
				? entity.getExternalUrl()
				: null;
		return new MaterialSummary(
				entity.getPublicUuid(),
				entity.getTitle(),
				entity.getKind(),
				fileSizeBytes,
				entity.getOwnerUserId(),
				entity.getCreatedAt(),
				LmsAssignmentProjection.publicUuid(assignment),
				LmsAssignmentProjection.coursePublicUuid(assignment),
				LmsAssignmentProjection.courseName(assignment),
				LmsAssignmentProjection.periodLabel(assignment),
				filePublicUuid,
				filename,
				contentType,
				externalUrl);
	}

	public List<MaterialSummary> toSummaryList(List<Material> entities) {
		return entities.stream().map(this::toSummary).toList();
	}

	public FileObjectResponse fileResponse(FileObject entity, String url) {
		return FileObjectResponse.fromEntity(entity, url);
	}
}
