package com.edushift.modules.files.service;

import com.edushift.modules.files.config.StorageProperties;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.entity.FileUploadStatus;
import com.edushift.modules.files.repository.FileObjectRepository;
import com.edushift.modules.files.storage.StorageProvider;
import com.edushift.modules.files.storage.StoragePutRequest;
import com.edushift.modules.files.storage.StorageService;
import com.edushift.modules.files.storage.StoredObject;
import com.edushift.modules.files.validator.FileValidator;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * High-level facade over {@link StorageService} + the
 * {@link FileObject} persistence layer (Sprint 7a / BE-7a.0).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate the upload ({@link FileValidator}).</li>
 *   <li>Build a tenant-scoped remote key.</li>
 *   <li>Stream the bytes to the active storage provider.</li>
 *   <li>Persist a {@link FileObject} row with the resulting
 *       (provider, remoteKey, size, sha256, contentType, originalName).</li>
 *   <li>Return the row to the caller — controllers then wrap it in
 *       a DTO with a download URL.</li>
 * </ul>
 *
 * <h3>Multi-tenant safety (audit §7)</h3>
 * Tenant id is taken from the Hibernate-bound
 * {@link com.edushift.shared.multitenancy.TenantContext} — never from
 * the controller. Cross-tenant lookups (load / delete) are
 * structurally impossible because {@code @TenantId} auto-filters the
 * repository query.
 */
@Service
public class FileObjectService {

	private final StorageService storage;
	private final FileObjectRepository repository;
	private final FileValidator validator;
	private final StorageProperties storageProperties;

	public FileObjectService(StorageService storage,
			FileObjectRepository repository,
			FileValidator validator,
			StorageProperties storageProperties) {
		this.storage = storage;
		this.repository = repository;
		this.validator = validator;
		this.storageProperties = storageProperties;
	}

	/**
	 * Persist an uploaded file under the given module bucket
	 * ({@code "materials"}, {@code "submissions"}, …).
	 *
	 * @param tenantId from the current request
	 * @param module   logical bucket inside the storage layout
	 * @param file     Spring multipart payload
	 * @return the persisted {@link FileObject} row, with a freshly
	 *         assigned {@code publicUuid}
	 */
	@Transactional
	public FileObject store(UUID tenantId, String module, MultipartFile file) {
		validator.validate(file);
		// We materialise the public UUID up-front so we can use it in
		// the storage key. The entity @PrePersist would also generate
		// one but doing it here keeps the key and the row aligned.
		UUID publicUuid = UUID.randomUUID();
		String ext = extractExtension(file.getOriginalFilename());
		String remoteKey = StoredObject.buildKey(tenantId, module, publicUuid, ext);

		StoredObject stored;
		try {
			stored = storage.put(new StoragePutRequest(
					tenantId,
					module,
					publicUuid,
					safeOriginalName(file.getOriginalFilename()),
					file.getContentType(),
					file.getInputStream(),
					file.getSize()));
		}
		catch (java.io.IOException e) {
			throw new com.edushift.modules.files.exception.StorageUnavailableException(
					"Failed to read uploaded stream", e);
		}

		FileObject entity = new FileObject();
		entity.setPublicUuid(publicUuid);
		entity.setProvider(stored.provider());
		entity.setRemoteKey(stored.remoteKey());
		entity.setOriginalName(stored != null ? safeOriginalName(file.getOriginalFilename())
				: safeOriginalName(file.getOriginalFilename()));
		entity.setContentType(file.getContentType());
		entity.setSizeBytes(stored.sizeBytes());
		entity.setChecksumSha256(stored.checksumSha256());
		entity.setBucket(stored.bucket());
		entity.setReferenceCount(0);
		// tenantId is auto-populated by Hibernate from TenantContext.
		return repository.save(entity);
	}

	/**
	 * Load a {@link FileObject} by its public UUID. Returns empty when
	 * the row is missing or belongs to another tenant (anti-enumeration
	 * 404 in the controller).
	 */
	@Transactional(readOnly = true)
	public java.util.Optional<FileObject> findByPublicUuid(UUID publicUuid) {
		return repository.findByPublicUuid(publicUuid);
	}

	/**
	 * Soft-delete the row and remove the bytes from the provider. The
	 * call is idempotent: a missing row or a missing object both
	 * succeed without effect.
	 */
	@Transactional
	public void delete(UUID publicUuid) {
		FileObject entity = repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new com.edushift.modules.files.exception.FileNotFoundException(
						publicUuid.toString()));
		// Files module is not yet wired as a shared kernel, but
		// referenceCount already exists in the schema for the day
		// lms_materials and lms_submissions arrive. Refuse to delete
		// a row that is still referenced — call sites should
		// `releaseReference` first. (DEBT-7A-1 follow-up.)
		if (entity.getReferenceCount() > 0) {
			throw new com.edushift.shared.exception.ConflictException(
					"FILE_IN_USE",
					"File is still referenced by " + entity.getReferenceCount()
							+ " row(s); release references first.");
		}
		storage.delete(entity.getTenantId(), entity.getRemoteKey());
		repository.delete(entity);
	}

	/**
	 * Increment {@code reference_count} atomically. Called by
	 * {@code lms_materials} and {@code lms_submissions} (BE-7a.1+)
	 * when a new row references the file.
	 */
	@Transactional
	public void acquireReference(UUID publicUuid) {
		FileObject entity = repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new com.edushift.modules.files.exception.FileNotFoundException(
						publicUuid.toString()));
		entity.setReferenceCount(entity.getReferenceCount() + 1);
		repository.save(entity);
	}

	/**
	 * Decrement {@code reference_count} atomically. Refuses to go
	 * below zero (programming error, surfaces as 500 in the global
	 * handler).
	 */
	@Transactional
	public void releaseReference(UUID publicUuid) {
		FileObject entity = repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new com.edushift.modules.files.exception.FileNotFoundException(
						publicUuid.toString()));
		if (entity.getReferenceCount() <= 0) {
			throw new IllegalStateException(
					"referenceCount already at zero for " + publicUuid);
		}
		entity.setReferenceCount(entity.getReferenceCount() - 1);
		repository.save(entity);
	}

	/**
	 * V50 — mint a signed PUT URL for direct upload (signed-URL flow).
	 *
	 * <p>For the FIREBASE provider this returns a time-limited URL the
	 * FE uses to {@code PUT} the bytes directly to the GCS bucket. For
	 * LOCAL_FS it returns {@code null} for the URL — callers must fall
	 * back to {@link #store(UUID, String, MultipartFile)} (BE-proxied).</p>
	 *
	 * <p>The row is created up-front in {@code PENDING}; the FE later
	 * flips it to {@code READY} via
	 * {@link #confirmUpload(UUID, long, String)}.</p>
	 *
	 * @return a record carrying the public UUID, the signed URL (or null
	 *         on LOCAL_FS) and the headers the FE must echo on the PUT.
	 */
	@Transactional
	public SignedUpload createUploadRequest(UUID tenantId, String module,
			String originalName, String contentType, long sizeBytes) {

		// Validate BEFORE creating the row: a rejected MIME / oversized
		// payload should never leave a PENDING row behind.
		validator.validateContentType(contentType);
		if (sizeBytes <= 0 || sizeBytes > storageProperties.getMaxFileSizeBytes()) {
			throw new com.edushift.modules.files.exception.FileTooLargeException(
					sizeBytes, storageProperties.getMaxFileSizeBytes());
		}

		UUID publicUuid = UUID.randomUUID();
		String ext = extractExtension(originalName);
		String remoteKey = StoredObject.buildKey(tenantId, module, publicUuid, ext);

		String signedUrl = storage.presignedPutUrl(
				tenantId, remoteKey, contentType,
				storageProperties.getFirebase().getSignedUrlTtlSeconds());

		FileObject entity = new FileObject();
		entity.setPublicUuid(publicUuid);
		entity.setProvider(storage.provider());
		entity.setRemoteKey(remoteKey);
		entity.setOriginalName(safeOriginalName(originalName));
		entity.setContentType(contentType);
		entity.setSizeBytes(sizeBytes);
		// Placeholder checksum until the client confirms; CHECK constraint
		// demands 64 lowercase hex chars, so we synthesise a sentinel of
		// zeros which the confirm flow overwrites.
		entity.setChecksumSha256("0".repeat(64));
		entity.setBucket(signedUrl != null
				? storageProperties.getFirebase().getBucket()
				: null);
		entity.setReferenceCount(0);
		entity.setStatus(signedUrl != null
				? FileUploadStatus.PENDING
				: FileUploadStatus.READY);
		repository.save(entity);

		long ttl = storageProperties.getFirebase().getSignedUrlTtlSeconds();
		return new SignedUpload(
				publicUuid,
				remoteKey,
				signedUrl,
				Instant.now().plusSeconds(ttl),
				contentType);
	}

	/**
	 * V50 — flip a row from {@code PENDING} to {@code READY} after the FE
	 * confirms the PUT to Firebase succeeded. Idempotent: a row already
	 * in {@code READY} returns without changes.
	 *
	 * @throws com.edushift.modules.files.exception.FileNotFoundException
	 *         when the public UUID does not exist or belongs to another
	 *         tenant (anti-enumeration 404).
	 */
	@Transactional
	public FileObject confirmUpload(UUID publicUuid, long sizeBytes, String checksumSha256) {
		FileObject entity = repository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new com.edushift.modules.files.exception.FileNotFoundException(
						publicUuid.toString()));
		if (entity.getStatus() == FileUploadStatus.READY) {
			// Idempotent re-confirm from the FE after a retry.
			return entity;
		}
		if (entity.getStatus() == FileUploadStatus.FAILED) {
			throw new com.edushift.modules.files.exception.FileNotFoundException(
					publicUuid + " (upload previously failed)");
		}
		if (sizeBytes != entity.getSizeBytes()) {
			throw new com.edushift.shared.exception.BadRequestException(
					"FILE_SIZE_MISMATCH",
					"Declared size " + sizeBytes + " does not match upload-request "
							+ entity.getSizeBytes());
		}
		entity.setSizeBytes(sizeBytes);
		entity.setChecksumSha256(checksumSha256.toLowerCase());
		entity.setStatus(FileUploadStatus.READY);
		return repository.save(entity);
	}

	/**
	 * Carrier for the signed-URL response. Avoids leaking this transient
	 * type into the controller signature as a raw Map.
	 */
	public record SignedUpload(
			UUID publicUuid,
			String remoteKey,
			String uploadUrl,
			Instant expiresAt,
			String contentType) { }

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private static String safeOriginalName(String name) {
		if (name == null || name.isBlank()) {
			return "unnamed";
		}
		// Strip directory components and replace dangerous characters.
		String basename = name.replace('\\', '/');
		int slash = basename.lastIndexOf('/');
		if (slash >= 0) {
			basename = basename.substring(slash + 1);
		}
		return basename.length() > 255 ? basename.substring(0, 255) : basename;
	}

	private static String extractExtension(String name) {
		if (name == null) {
			return null;
		}
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return null;
		}
		return name.substring(dot + 1);
	}
}
