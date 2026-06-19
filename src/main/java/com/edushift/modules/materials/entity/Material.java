package com.edushift.modules.materials.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/**
 * <strong>Material</strong> — a per-section LMS resource uploaded by a
 * teacher (or TENANT_ADMIN) (Sprint 7a / BE-7a.1).
 *
 * <p>Two flavours (see {@link MaterialKind}):
 * <ul>
 *   <li>{@code FILE} - an uploaded binary; {@code filePublicUuid}
 *       references the {@code lms_file_objects} row (BE-7a.0 shared
 *       kernel). {@code externalUrl} is NULL.</li>
 *   <li>{@code VIDEO_LINK} - an external URL (YouTube, Vimeo, etc.);
 *       {@code filePublicUuid} is NULL and {@code externalUrl} carries
 *       the link.</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code kind=FILE} ⟹ {@code filePublicUuid} NOT NULL ∧
 *       {@code externalUrl} IS NULL (DB CHECK).</li>
 *   <li>{@code kind=VIDEO_LINK} ⟹ {@code filePublicUuid} IS NULL ∧
 *       {@code externalUrl} NOT NULL (DB CHECK).</li>
 *   <li>Soft-delete is via {@code @SQLDelete} + base
 *       {@code @SQLRestriction("deleted = false")}.</li>
 *   <li>Section cannot be deleted while materials reference it
 *       (FK ON DELETE RESTRICT).</li>
 * </ul>
 *
 * <h3>Multi-tenant</h3>
 * Auto-filtered by Hibernate's {@code @TenantId} discriminator; no
 * query in the repository or service writes raw SQL bypassing it.
 */
@Entity
@Table(
		name = "lms_materials",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_lms_materials_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_lms_materials_tenant_section_created",
						columnList = "tenant_id, section_id, created_at"),
				@Index(name = "idx_lms_materials_tenant_owner_created",
						columnList = "tenant_id, owner_user_id, created_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "title", "kind"})
@SQLDelete(sql = "UPDATE edushift.lms_materials "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Material extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_lms_materials_section"))
	private com.edushift.modules.academic.section.entity.Section section;

	@Column(name = "file_public_uuid", columnDefinition = "uuid")
	private UUID filePublicUuid;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 16)
	private MaterialKind kind;

	@Column(name = "external_url", length = 2048)
	private String externalUrl;

	@Column(name = "owner_user_id", nullable = false, columnDefinition = "uuid")
	private UUID ownerUserId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
