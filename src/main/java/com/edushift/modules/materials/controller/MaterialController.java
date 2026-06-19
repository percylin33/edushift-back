package com.edushift.modules.materials.controller;

import com.edushift.modules.materials.dto.CreateLinkMaterialRequest;
import com.edushift.modules.materials.dto.CreateUploadMaterialRequest;
import com.edushift.modules.materials.dto.MaterialResponse;
import com.edushift.modules.materials.dto.MaterialSummary;
import com.edushift.modules.materials.dto.UpdateMaterialRequest;
import com.edushift.modules.materials.service.MaterialService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST adapter for the LMS materials module (Sprint 7a / BE-7a.1).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Materials endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Authority</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/v1/sections/{sectionPublicUuid}/materials (upload)</td>
 *       <td>LMS_MATERIAL_WRITE</td>
 *       <td>{@link MaterialResponse} (201)</td></tr>
 *   <tr><td>POST</td>
 *       <td>/v1/sections/{sectionPublicUuid}/materials (link)</td>
 *       <td>LMS_MATERIAL_WRITE</td>
 *       <td>{@link MaterialResponse} (201)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/sections/{sectionPublicUuid}/materials</td>
 *       <td>LMS_MATERIAL_READ</td>
 *       <td>{@code Page<}{@link MaterialSummary}{@code >} (200)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/materials/{publicUuid}</td>
 *       <td>LMS_MATERIAL_READ</td>
 *       <td>{@link MaterialResponse} (200)</td></tr>
 *   <tr><td>PATCH</td>
 *       <td>/v1/materials/{publicUuid}</td>
 *       <td>LMS_MATERIAL_WRITE</td>
 *       <td>{@link MaterialResponse} (200)</td></tr>
 *   <tr><td>DELETE</td>
 *       <td>/v1/materials/{publicUuid}</td>
 *       <td>LMS_MATERIAL_DELETE</td>
 *       <td>(204)</td></tr>
 * </table>
 *
 * <h3>Authorisation</h3>
 * Coarse-grained {@code hasAuthority(...)} at the controller; the
 * service-level {@code section-enrollment} check
 * ({@code docs/modules/materials.md} D-MAT-06) is performed in the
 * service for fine-grained 403s.
 *
 * <h3>Cross-tenant</h3>
 * Section, section-id, and material lookups go through tenant-aware
 * repositories; cross-tenant access resolves as 404 (anti-enumeration
 * per D-MAT-04).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Materials")
public class MaterialController {

	private final MaterialService materialService;
	private final CurrentUserProvider currentUserProvider;

	@PostMapping(
			path = "/sections/{sectionPublicUuid}/materials",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('LMS_MATERIAL_WRITE')")
	@Operation(summary = "Upload a binary material into a section")
	public ResponseEntity<ApiResponse<MaterialResponse>> createUpload(
			@PathVariable UUID sectionPublicUuid,
			@RequestPart("file") MultipartFile file,
			@Valid @RequestPart("metadata") CreateUploadMaterialRequest metadata) {
		MaterialResponse response = materialService.createUpload(
				sectionPublicUuid, metadata, file, currentUser());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PostMapping(
			path = "/sections/{sectionPublicUuid}/materials",
			consumes = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAuthority('LMS_MATERIAL_WRITE')")
	@Operation(summary = "Register a video link as a material")
	public ResponseEntity<ApiResponse<MaterialResponse>> createLink(
			@PathVariable UUID sectionPublicUuid,
			@Valid @RequestBody CreateLinkMaterialRequest request) {
		MaterialResponse response = materialService.createLink(
				sectionPublicUuid, request, currentUser());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@GetMapping("/sections/{sectionPublicUuid}/materials")
	@PreAuthorize("hasAuthority('LMS_MATERIAL_READ')")
	@Operation(summary = "List materials of a section (paged)")
	public Page<MaterialSummary> list(
			@PathVariable UUID sectionPublicUuid,
			@PageableDefault(size = 20) Pageable pageable) {
		return materialService.listBySection(sectionPublicUuid, pageable);
	}

	@GetMapping("/materials/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_MATERIAL_READ')")
	@Operation(summary = "Fetch a material by its public UUID")
	public ApiResponse<MaterialResponse> get(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(materialService.getByPublicUuid(publicUuid));
	}

	@PatchMapping("/materials/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_MATERIAL_WRITE')")
	@Operation(summary = "Patch material metadata (title, description, kind, externalUrl)")
	public ApiResponse<MaterialResponse> patch(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateMaterialRequest request) {
		return ApiResponse.ok(materialService.patch(publicUuid, request));
	}

	@DeleteMapping("/materials/{publicUuid}")
	@PreAuthorize("hasAuthority('LMS_MATERIAL_DELETE')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Soft-delete a material and release its file reference")
	public void delete(@PathVariable UUID publicUuid) {
		materialService.delete(publicUuid);
	}

	private UUID currentUser() {
		return currentUserProvider.currentUserId().orElse(null);
	}
}
