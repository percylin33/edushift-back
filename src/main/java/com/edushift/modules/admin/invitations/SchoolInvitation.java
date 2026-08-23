package com.edushift.modules.admin.invitations;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Super-Admin invitation to create a new school.
 *
 * <p>Platform-level (no {@code tenant_id}) because the tenant does not exist
 * until the founder redeems the token. Lifecycle is derived from timestamps
 * the same way as {@code UserInvitation}.
 */
@Entity
@Table(
		name = "school_invitations",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_school_invitations_public_uuid", columnNames = "public_uuid")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "email", "expiresAt"})
@SQLDelete(sql = "UPDATE edushift.school_invitations "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class SchoolInvitation extends AuditableEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "email", nullable = false, length = 254)
	private String email;

	@Column(name = "token", nullable = false, length = 64)
	private String token;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "created_tenant_id", columnDefinition = "uuid")
	private UUID createdTenantId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (email != null) {
			email = email.trim().toLowerCase();
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

	public boolean isAccepted() {
		return acceptedAt != null;
	}

	public boolean isCancelled() {
		return cancelledAt != null;
	}

	public boolean isExpired(Instant now) {
		return expiresAt != null && !expiresAt.isAfter(now);
	}

	public boolean isPending(Instant now) {
		return !isAccepted() && !isCancelled() && !isExpired(now);
	}

	public void markAccepted(Instant when, UUID tenantId) {
		this.acceptedAt = when;
		this.createdTenantId = tenantId;
	}

	public void markCancelled(Instant when) {
		this.cancelledAt = when;
	}
}
