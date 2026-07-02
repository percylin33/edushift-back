package com.edushift.modules.files.controller;

import com.edushift.modules.files.config.StorageProperties;
import com.edushift.modules.files.dto.FileObjectResponse;
import com.edushift.modules.files.dto.UploadConfirmation;
import com.edushift.modules.files.dto.UploadRequest;
import com.edushift.modules.files.dto.UploadRequestResponse;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.exception.FileNotFoundException;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.files.storage.FirebaseStorageService;
import com.edushift.modules.files.storage.StorageService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST adapter for the {@code files} module (Sprint 7a / BE-7a.0).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>File endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/files/{publicUuid}</td>
 *       <td>authenticated</td>
 *       <td>{@link FileObjectResponse} (JSON metadata + download URL)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/files/{publicUuid}/download</td>
 *       <td>authenticated</td>
 *       <td>Binary payload or 302 to a signed URL</td></tr>
 *   <tr><td>DELETE</td>
 *       <td>/v1/files/{publicUuid}</td>
 *       <td>tenant admin / teacher</td>
 *       <td>204 No Content</td></tr>
 * </table>
 *
 * <h3>Multi-tenant safety (audit §12)</h3>
 * Cross-tenant GET/DELETE return 404 (no leak of existence). The
 * service layer relies on Hibernate's {@code @TenantId} auto-filter
 * — the row simply is invisible to the bearer's tenant.
 */
@Slf4j
@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files",
		description = "LMS binary registry + download endpoint. Cross-tenant "
				+ "access returns 404 (anti-enumeration).")
public class FileObjectController {

	private final FileObjectService fileObjectService;
	private final StorageService storageService;
	private final StorageProperties storageProperties;
	private final CurrentUserProvider currentUserProvider;

	// =====================================================================
	// GET /v1/files/{publicUuid}
	// =====================================================================

	@GetMapping(value = "/{publicUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Get file metadata + a downloadable URL")
	public ResponseEntity<ApiResponse<FileObjectResponse>> get(
			@PathVariable UUID publicUuid) {

		FileObject entity = fileObjectService.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new FileNotFoundException(publicUuid.toString()));

		String url = resolveDownloadUrl(entity, publicUuid);
		return ResponseEntity.ok(ApiResponse.ok(
				FileObjectResponse.fromEntity(entity, url)));
	}

	// =====================================================================
	// GET /v1/files/{publicUuid}/download
	// =====================================================================

	@GetMapping("/{publicUuid}/download")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Download the bytes",
			description = "Streams the binary with the original filename in the "
					+ "Content-Disposition header. When the active provider is "
					+ "FIREBASE with serve-from-controller=false, the call is "
					+ "redirected (302) to a pre-signed GCS URL.")
	public ResponseEntity<?> download(@PathVariable UUID publicUuid) {

		FileObject entity = fileObjectService.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new FileNotFoundException(publicUuid.toString()));

		// Firebase with serve-from-controller=false: redirect to a
		// pre-signed URL so the heavy bytes never touch this server.
		if (usesSignedUrlRedirect()) {
			String signed = storageService.presignedGetUrl(
					entity.getTenantId(), entity.getRemoteKey(),
					storageProperties.getFirebase().getSignedUrlTtlSeconds());
			if (signed != null) {
				return ResponseEntity.status(HttpStatus.FOUND)
						.location(URI.create(signed))
						.build();
			}
		}

		// Stream from the provider. InputStream is closed by Spring
		// when the response is fully written.
		InputStream stream = storageService.open(
				entity.getTenantId(), entity.getRemoteKey());

		return ResponseEntity.ok()
				.contentType(resolveContentType(entity))
				.contentLength(entity.getSizeBytes())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + safeFilename(entity.getOriginalName()) + "\"")
				.cacheControl(CacheControl.noStore().mustRevalidate())
				.body(new InputStreamResource(stream));
	}

	// =====================================================================
	// DELETE /v1/files/{publicUuid}
	// =====================================================================

	@DeleteMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Soft-delete the file row and remove the binary")
	public ResponseEntity<ApiResponse<Void>> delete(
			@PathVariable UUID publicUuid) {
		fileObjectService.delete(publicUuid);
		return ResponseEntity.ok(ApiResponse.ok());
	}

	// =====================================================================
	// POST /v1/files  (BE-proxied multipart upload — fallback path)
	// =====================================================================

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Upload a file via multipart/form-data (BE-proxied)",
			description = "The classic upload path: the BE streams the bytes "
					+ "to the active storage provider. Use this on LOCAL_FS or "
					+ "when the signed-URL flow is not desired.")
	public ResponseEntity<ApiResponse<FileObjectResponse>> upload(
			@RequestParam("module") String module,
			@RequestPart("file") MultipartFile file) throws java.io.IOException {

		UUID tenantId = currentUserProvider.currentTenantId()
				.orElseThrow(() -> new com.edushift.shared.exception.UnauthorizedException(
						"NO_TENANT",
						"Authenticated user has no tenant binding"));

		FileObject entity = fileObjectService.store(tenantId, module, file);
		String url = resolveDownloadUrl(entity, entity.getPublicUuid());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(FileObjectResponse.fromEntity(entity, url)));
	}

	// =====================================================================
	// POST /v1/files/upload-requests  (V50, signed-URL flow)
	// =====================================================================

	@PostMapping(value = "/upload-requests", produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Mint a signed PUT URL for direct upload to Firebase",
			description = "Returns a short-lived URL the client can PUT the bytes "
					+ "to directly. For LOCAL_FS the URL is null — the FE must "
					+ "fall back to the BE-proxied multipart endpoint instead.")
	public ResponseEntity<ApiResponse<UploadRequestResponse>> createUploadRequest(
			@Valid @RequestBody UploadRequest request) {

		UUID tenantId = currentUserProvider.currentTenantId()
				.orElseThrow(() -> new com.edushift.shared.exception.UnauthorizedException(
						"NO_TENANT",
						"Authenticated user has no tenant binding"));

		FileObjectService.SignedUpload signed = fileObjectService.createUploadRequest(
				tenantId, request.module(), request.originalName(),
				request.contentType(), request.sizeBytes());

		Map<String, String> headers = new LinkedHashMap<>();
		if (signed.uploadUrl() != null) {
			headers.put("Content-Type", signed.contentType());
		}

		return ResponseEntity.ok(ApiResponse.ok(new UploadRequestResponse(
				storageService.provider().name(),
				signed.publicUuid(),
				signed.uploadUrl(),
				signed.expiresAt(),
				headers)));
	}

	// =====================================================================
	// POST /v1/files/{publicUuid}/confirm  (V50, signed-URL flow)
	// =====================================================================

	@PostMapping(value = "/{publicUuid}/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Confirm the PUT to Firebase succeeded",
			description = "Flips the row from PENDING to READY and persists the "
					+ "client-computed SHA-256. Idempotent: re-confirming a READY "
					+ "row is a no-op.")
	public ResponseEntity<ApiResponse<FileObjectResponse>> confirmUpload(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UploadConfirmation confirmation) {

		FileObject entity = fileObjectService.confirmUpload(
				publicUuid, confirmation.sizeBytes(), confirmation.checksumSha256());

		String url = resolveDownloadUrl(entity, publicUuid);
		return ResponseEntity.ok(ApiResponse.ok(
				FileObjectResponse.fromEntity(entity, url)));
	}

	// =====================================================================
	// helpers
	// =====================================================================

	/**
	 * True when the active provider is Firebase AND the property
	 * {@code app.storage.local-fs.serve-from-controller} is false.
	 * In that mode the download is a 302 to a pre-signed GCS URL
	 * instead of a streamed body.
	 */
	private boolean usesSignedUrlRedirect() {
		return !storageProperties.getLocalFs().isServeFromController()
				&& storageService instanceof FirebaseStorageService;
	}

	/**
	 * Build a stable download URL for {@link FileObjectResponse}.
	 * For the controller-routed providers (LOCAL_FS, or FIREBASE with
	 * {@code serve-from-controller=true}) the URL is a path against
	 * the same backend. For FIREBASE with the controller bypass we
	 * eagerly sign and return the absolute URL.
	 */
	private String resolveDownloadUrl(FileObject entity, UUID publicUuid) {
		if (usesSignedUrlRedirect()) {
			String signed = storageService.presignedGetUrl(
					entity.getTenantId(), entity.getRemoteKey(),
					storageProperties.getFirebase().getSignedUrlTtlSeconds());
			if (signed != null) {
				return signed;
			}
		}
		// Controller path (relative; FE prefixes with the API base).
		return "/api/v1/files/" + publicUuid + "/download";
	}

	private static MediaType resolveContentType(FileObject entity) {
		String ct = entity.getContentType();
		if (ct == null || ct.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		try {
			return MediaType.parseMediaType(ct);
		}
		catch (Exception e) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}

	private static String safeFilename(String name) {
		if (name == null || name.isBlank()) {
			return "download";
		}
		// RFC 6266: filenames in Content-Disposition must not contain
		// quotes or newlines. We also strip control characters.
		String safe = name.replaceAll("[\"\\r\\n]", "");
		return safe.length() > 200 ? safe.substring(0, 200) : safe;
	}
}
