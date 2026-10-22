package com.edushift.modules.disclosures.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable, tenant-scoped copy of the content approved and published for families. */
@Entity
@Table(name = "annual_disclosure_publications", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "versionNumber", "publishedAt"})
public class AnnualDisclosurePublication extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "package_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID packageId;

	@Column(name = "version_number", nullable = false, updatable = false)
	private int versionNumber;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "content", nullable = false, updatable = false, columnDefinition = "jsonb")
	private java.util.Map<String, Object> content = new HashMap<>();

	@Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
	private String contentSha256;

	@Column(name = "approved_at", nullable = false, updatable = false)
	private Instant approvedAt;

	@Column(name = "approved_by", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID approvedBy;

	@Column(name = "published_at", nullable = false, updatable = false)
	private Instant publishedAt;

	@Column(name = "published_by", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID publishedBy;

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
		if (content == null) content = new HashMap<>();
	}
}
