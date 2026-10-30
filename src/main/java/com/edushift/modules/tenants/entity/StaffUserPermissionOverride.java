package com.edushift.modules.tenants.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** A per-user grant or revocation layered over the assigned staff profiles. */
@Entity
@Table(name = "staff_user_permission_overrides", schema = "edushift",
		indexes = @Index(name = "ix_staff_user_permissions_tenant_user",
				columnList = "tenant_id, user_id"))
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE edushift.staff_user_permission_overrides "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
public class StaffUserPermissionOverride extends TenantAwareEntity {

	@Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID userId;

	@Column(name = "authority", nullable = false, updatable = false, length = 64)
	private String authority;

	@Column(name = "granted", nullable = false)
	private boolean granted;

	@Column(name = "deleted_at")
	private Instant deletedAt;
}
