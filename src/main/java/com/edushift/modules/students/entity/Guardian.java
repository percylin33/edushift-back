package com.edushift.modules.students.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * Parent / responsible adult linked to one or more {@link Student}s.
 *
 * <h3>Why per-tenant, not global</h3>
 * Two tenants might have a guardian whose document number is identical
 * (same person, two schools, different families). Forcing global
 * uniqueness would force tenants to share a single canonical record;
 * keeping it per-tenant respects multi-tenancy and avoids cross-tenant
 * data leakage. The trade-off is data duplication when a parent works
 * at multiple institutions, which is the rare case.
 *
 * <h3>Sibling sharing</h3>
 * The same guardian record IS shared between siblings <em>within the
 * same tenant</em>. The repository's {@code findByDocument...} lookup
 * is the entry point used by {@link com.edushift.modules.students.service.StudentGuardianService}
 * to "promote" an add-guardian-by-document call into a link without
 * re-creating the row.
 */
@Entity
@Table(
		name = "guardians",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_guardians_public_uuid", columnNames = "public_uuid")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "documentType", "documentNumber", "firstName", "lastName"})
@SQLDelete(sql = "UPDATE edushift.guardians "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Guardian extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 20)
	private DocumentType documentType;

	@Column(name = "document_number", nullable = false, length = 20)
	private String documentNumber;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "email", length = 254)
	private String email;

	@Column(name = "phone", length = 32)
	private String phone;

	@Column(name = "occupation", length = 100)
	private String occupation;

	@Column(name = "user_id", columnDefinition = "uuid")
	private UUID userId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		normalizeEmail();
	}

	@PreUpdate
	private void onPreUpdate() {
		normalizeEmail();
	}

	private void normalizeEmail() {
		if (email != null) {
			email = email.trim();
			if (email.isEmpty()) email = null;
			else email = email.toLowerCase();
		}
	}

	@Override
	public void markDeleted() {
		super.markDeleted();
		this.deletedAt = Instant.now();
	}

	@Override
	public void restore() {
		super.restore();
		this.deletedAt = null;
	}

	public String fullName() {
		StringBuilder sb = new StringBuilder();
		if (firstName != null) sb.append(firstName);
		if (lastName != null) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(lastName);
		}
		return sb.toString();
	}
}
