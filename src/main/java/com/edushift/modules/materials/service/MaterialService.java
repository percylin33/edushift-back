package com.edushift.modules.materials.service;

import com.edushift.modules.materials.dto.CreateLinkMaterialRequest;
import com.edushift.modules.materials.dto.CreateUploadMaterialRequest;
import com.edushift.modules.materials.dto.MaterialResponse;
import com.edushift.modules.materials.dto.MaterialSummary;
import com.edushift.modules.materials.dto.UpdateMaterialRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public contract for the LMS materials module (Sprint 7a / BE-7a.1).
 *
 * <p>The controller layer wires tenant context (via JWT) before
 * delegating; the service resolves the bearer-owned
 * {@code ownerUserId} from the security context.
 */
public interface MaterialService {

	/**
	 * Upload a binary as a new material in the given section.
	 * Returns the persisted material with the file metadata inlined.
	 */
	MaterialResponse createUpload(UUID sectionPublicUuid,
			CreateUploadMaterialRequest request,
			MultipartFile file,
			UUID ownerUserId);

	/**
	 * Register an external video link as a material.
	 */
	MaterialResponse createLink(UUID sectionPublicUuid,
			CreateLinkMaterialRequest request,
			UUID ownerUserId);

	/**
	 * List materials of a section (paged). The tenant filter is
	 * applied by Hibernate via {@code @TenantId}.
	 */
	Page<MaterialSummary> listBySection(UUID sectionPublicUuid, Pageable pageable);

	/** Fetch a single material. Anti-enumeration: 404 on cross-tenant. */
	MaterialResponse getByPublicUuid(UUID publicUuid);

	/** Patch metadata. Rejects empty bodies. */
	MaterialResponse patch(UUID publicUuid, UpdateMaterialRequest request);

	/** Soft-delete the row and release the file reference. */
	void delete(UUID publicUuid);
}
