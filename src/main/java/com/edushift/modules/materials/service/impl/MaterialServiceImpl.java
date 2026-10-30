package com.edushift.modules.materials.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.materials.dto.CreateLinkMaterialRequest;
import com.edushift.modules.materials.dto.CreateUploadMaterialRequest;
import com.edushift.modules.materials.dto.MaterialResponse;
import com.edushift.modules.materials.dto.MaterialSummary;
import com.edushift.modules.materials.dto.UpdateMaterialRequest;
import com.edushift.modules.materials.entity.Material;
import com.edushift.modules.materials.entity.MaterialKind;
import com.edushift.modules.materials.exception.InconsistentPayloadException;
import com.edushift.modules.materials.exception.MaterialNotFoundException;
import com.edushift.modules.materials.exception.RecordEmptyPatchException;
import com.edushift.modules.materials.exception.SectionNotFoundException;
import com.edushift.modules.materials.mapper.MaterialMapper;
import com.edushift.modules.materials.repository.MaterialRepository;
import com.edushift.modules.materials.service.MaterialService;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.support.LmsAssignmentBinder;
import com.edushift.shared.security.CurrentUserProvider;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Default {@link MaterialService} implementation
 * (Sprint 7a / BE-7a.1).
 *
 * <h3>Multi-tenant safety (audit §6)</h3>
 * Every read & write goes through {@code @TenantId}-filtered
 * repository methods, so cross-tenant lookups silently resolve as
 * {@code Optional.empty()}. The {@code ownerUserId} comes from the
 * {@link CurrentUserProvider} so it can never be spoofed via the
 * request body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

	private final MaterialRepository materialRepository;
	private final SectionRepository sectionRepository;
	private final FileObjectService fileObjectService;
	private final MaterialMapper materialMapper;
	private final CurrentUserProvider currentUserProvider;
	private final LmsAssignmentBinder lmsAssignmentBinder;

	@Override
	@Transactional
	public MaterialResponse createUpload(UUID sectionPublicUuid,
			CreateUploadMaterialRequest request,
			MultipartFile file,
			UUID ownerUserId) {
		Section section = requireSection(sectionPublicUuid);
		UUID effectiveOwner = requireOwner(ownerUserId);
		TeacherAssignment assignment = lmsAssignmentBinder.requireForCreate(
				request.teacherAssignmentPublicUuid(), section);

		FileObject stored = fileObjectService.store(currentTenant(), "materials", file);

		Material entity = new Material();
		entity.setSection(section);
		entity.setTeacherAssignment(assignment);
		entity.setFilePublicUuid(stored.getPublicUuid());
		entity.setTitle(request.title());
		entity.setDescription(request.description());
		entity.setKind(MaterialKind.FILE);
		entity.setExternalUrl(null);
		entity.setOwnerUserId(effectiveOwner);
		Material saved = materialRepository.save(entity);

		fileObjectService.acquireReference(stored.getPublicUuid());

		String url = String.format("/api/v1/files/%s/download", stored.getPublicUuid());
		return materialMapper.toResponse(saved, materialMapper.fileResponse(stored, url));
	}

	@Override
	@Transactional
	public MaterialResponse createLink(UUID sectionPublicUuid,
			CreateLinkMaterialRequest request,
			UUID ownerUserId) {
		Section section = requireSection(sectionPublicUuid);
		UUID effectiveOwner = requireOwner(ownerUserId);
		TeacherAssignment assignment = lmsAssignmentBinder.requireForCreate(
				request.teacherAssignmentPublicUuid(), section);

		if (request.kind() != MaterialKind.VIDEO_LINK) {
			throw new InconsistentPayloadException(
					"createLink requires kind=VIDEO_LINK; got " + request.kind());
		}
		try {
			new URL(request.externalUrl());
		}
		catch (MalformedURLException e) {
			throw new InconsistentPayloadException(
					"externalUrl is not a valid URL: " + request.externalUrl());
		}

		Material entity = new Material();
		entity.setSection(section);
		entity.setTeacherAssignment(assignment);
		entity.setFilePublicUuid(null);
		entity.setTitle(request.title());
		entity.setDescription(request.description());
		entity.setKind(MaterialKind.VIDEO_LINK);
		entity.setExternalUrl(request.externalUrl());
		entity.setOwnerUserId(effectiveOwner);
		Material saved = materialRepository.save(entity);
		return materialMapper.toResponse(saved, null);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<MaterialSummary> listBySection(UUID sectionPublicUuid, Pageable pageable) {
		return listBySection(sectionPublicUuid, null, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<MaterialSummary> listBySection(
			UUID sectionPublicUuid, UUID assignmentPublicUuid, Pageable pageable) {
		Optional<Section> section = sectionRepository.findByPublicUuid(sectionPublicUuid);
		if (section.isEmpty()) {
			return Page.empty(pageable);
		}
		if (assignmentPublicUuid != null) {
			TeacherAssignment assignment = lmsAssignmentBinder.requireForFilter(
					assignmentPublicUuid, section.get());
			return materialRepository
					.findAllBySectionAndTeacherAssignmentOrderByCreatedAtDesc(
							section.get(), assignment, pageable)
					.map(materialMapper::toSummary);
		}
		return materialRepository
				.findAllBySectionOrderByCreatedAtDesc(section.get(), pageable)
				.map(materialMapper::toSummary);
	}

	@Override
	@Transactional(readOnly = true)
	public MaterialResponse getByPublicUuid(UUID publicUuid) {
		Material entity = requireMaterial(publicUuid);
		FileObjectResponseProxy proxy = resolveFileProxy(entity);
		return materialMapper.toResponse(entity, proxy.response());
	}

	@Override
	@Transactional
	public MaterialResponse patch(UUID publicUuid, UpdateMaterialRequest request) {
		if (isAllNull(request)) {
			throw new RecordEmptyPatchException();
		}
		Material entity = requireMaterial(publicUuid);

		if (request.title() != null) {
			entity.setTitle(request.title());
		}
		if (request.description() != null) {
			entity.setDescription(request.description());
		}
		if (request.kind() != null && request.kind() != entity.getKind()) {
			// Switching kind mid-life is rare; the only allowed switch
			// is FILE -> VIDEO_LINK (we release the file reference).
			// VIDEO_LINK -> FILE is unsupported via PATCH: the teacher
			// must create a new material (D-MAT-01).
			if (request.kind() == MaterialKind.VIDEO_LINK
					&& entity.getKind() == MaterialKind.FILE
					&& entity.getFilePublicUuid() != null) {
				UUID oldFile = entity.getFilePublicUuid();
				entity.setFilePublicUuid(null);
				entity.setExternalUrl(request.externalUrl());
				entity.setKind(MaterialKind.VIDEO_LINK);
				fileObjectService.releaseReference(oldFile);
			}
			else {
				throw new InconsistentPayloadException(
						"kind switch " + entity.getKind() + " -> "
								+ request.kind() + " is not supported via PATCH; "
								+ "create a new material instead.");
			}
		}
		else if (request.externalUrl() != null) {
			// Same-kind patch: validate consistency against the
			// current kind.
			if (entity.getKind() == MaterialKind.FILE) {
				throw new InconsistentPayloadException(
						"kind=FILE does not accept externalUrl; create a new VIDEO_LINK material instead.");
			}
			try {
				new URL(request.externalUrl());
			}
			catch (MalformedURLException e) {
				throw new InconsistentPayloadException(
						"externalUrl is not a valid URL: " + request.externalUrl());
			}
			entity.setExternalUrl(request.externalUrl());
		}

		Material saved = materialRepository.save(entity);
		FileObjectResponseProxy proxy = resolveFileProxy(saved);
		return materialMapper.toResponse(saved, proxy.response());
	}

	@Override
	@Transactional
	public void delete(UUID publicUuid) {
		Material entity = requireMaterial(publicUuid);
		UUID filePublicUuid = entity.getFilePublicUuid();
		materialRepository.delete(entity);
		if (filePublicUuid != null) {
			try {
				fileObjectService.releaseReference(filePublicUuid);
			}
			catch (RuntimeException ex) {
				// log but don't fail the delete; the material row is
				// already gone and the file will eventually be GC'd
				// by housekeeping (DEBT-7A-18 future).
				log.warn("releaseReference failed for material {} file {}: {}",
						publicUuid, filePublicUuid, ex.getMessage());
			}
		}
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private Material requireMaterial(UUID publicUuid) {
		return materialRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new MaterialNotFoundException(publicUuid.toString()));
	}

	private Section requireSection(UUID sectionPublicUuid) {
		return sectionRepository.findByPublicUuid(sectionPublicUuid)
				.orElseThrow(() -> new SectionNotFoundException(sectionPublicUuid.toString()));
	}

	private UUID requireOwner(UUID supplied) {
		// Prefer the explicit param (the controller pulls it from
		// the JWT); fall back to the security context for safety.
		if (supplied != null) return supplied;
		return currentUserProvider.currentUserId().orElse(null);
	}

	private UUID currentTenant() {
		return currentUserProvider.currentTenantId().orElseThrow(() ->
				new com.edushift.shared.exception.UnauthorizedException(
						"Authenticated tenant is required"));
	}

	private static boolean isAllNull(UpdateMaterialRequest r) {
		return r.title() == null
				&& r.description() == null
				&& r.kind() == null
				&& r.externalUrl() == null;
	}

	/**
	 * Lazy-resolve the {@link com.edushift.modules.files.dto.FileObjectResponse}
	 * for a material. Returns a proxy with {@code response=null} when the
	 * material is a VIDEO_LINK or its file reference is dangling.
	 */
	private FileObjectResponseProxy resolveFileProxy(Material entity) {
		if (entity.getFilePublicUuid() == null) {
			return FileObjectResponseProxy.empty();
		}
		return fileObjectService.findByPublicUuid(entity.getFilePublicUuid())
				.map(fo -> {
					String url = String.format("/api/v1/files/%s/download", fo.getPublicUuid());
					return FileObjectResponseProxy.of(
							materialMapper.fileResponse(fo, url));
				})
				.orElseGet(FileObjectResponseProxy::empty);
	}

	/**
	 * Tiny carrier for the optional nested {@code MaterialResponse.file}
	 * field. Replaces an {@code Optional<...>} for nullability in
	 * the mapper signature.
	 */
	private record FileObjectResponseProxy(
			com.edushift.modules.files.dto.FileObjectResponse response) {

		static FileObjectResponseProxy empty() {
			return new FileObjectResponseProxy(null);
		}

		static FileObjectResponseProxy of(
				com.edushift.modules.files.dto.FileObjectResponse r) {
			return new FileObjectResponseProxy(r);
		}
	}
}
