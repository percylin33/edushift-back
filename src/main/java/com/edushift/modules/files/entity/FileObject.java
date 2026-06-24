package com.edushift.modules.files.entity;

import com.edushift.modules.files.storage.StorageProvider;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Metadata-only registry of an uploaded binary (Sprint 7a / BE-7a.0).
 *
 * <p>The bytes live in the {@link StorageProvider} (Firebase in
 * prod/staging, local filesystem in dev/test). This row is the
 * authoritative index of those bytes: who owns them, what they are,
 * how big, what checksum, and how many domain rows currently reference
 * them (used by the housekeeping job, future work, to GC orphans).
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>One row per non-deleted {@code (tenant_id, provider, remote_key)}
 *       triple (enforced by
 *       {@code uk_file_objects_tenant_provider_remote_key_active}).</li>
 *   <li>{@code checksum_sha256} is always lowercase hex 64 chars (DB CHECK).</li>
 *   <li>Soft-delete is via {@code @SQLDelete} + the base
 *       {@code @SQLRestriction("deleted = false")}.</li>
 * </ul>
 */
@Entity
@Table(
		name = "lms_file_objects",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_file_objects_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_file_objects_tenant",
						columnList = "tenant_id"),
				@Index(name = "idx_file_objects_tenant_created",
						columnList = "tenant_id, created_at"),
				@Index(name = "idx_file_objects_tenant_checksum",
						columnList = "tenant_id, checksum_sha256")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "provider", "remoteKey", "originalName", "sizeBytes"})
@SQLDelete(sql = "UPDATE edushift.lms_file_objects "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
@SQLRestriction("deleted = false")
public class FileObject extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 16)
	private StorageProvider provider;

	@Column(name = "remote_key", nullable = false, length = 512)
	private String remoteKey;

	@Column(name = "original_name", nullable = false, length = 255)
	private String originalName;

	@Column(name = "content_type", nullable = false, length = 127)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "checksum_sha256", nullable = false, length = 64)
	private String checksumSha256;

	/** GCS bucket name (FIREBASE only); null for LOCAL_FS. */
	@Column(name = "bucket", length = 128)
	private String bucket;

	@Column(name = "reference_count", nullable = false)
	private int referenceCount;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
