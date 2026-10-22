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

/** A built-in staff access profile assigned to one user in one tenant. */
@Entity
@Table(name = "staff_user_profile_assignments", schema = "edushift",
		indexes = @Index(name = "ix_staff_profile_assignments_tenant_user",
				columnList = "tenant_id, user_id"))
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE edushift.staff_user_profile_assignments "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
public class StaffUserProfileAssignment extends TenantAwareEntity {

	@Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID userId;

	@Column(name = "profile_code", nullable = false, updatable = false, length = 40)
	private String profileCode;

	@Column(name = "deleted_at")
	private Instant deletedAt;
}
