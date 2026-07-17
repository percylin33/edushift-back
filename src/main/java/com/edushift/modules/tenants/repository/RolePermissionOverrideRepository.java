package com.edushift.modules.tenants.repository;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.tenants.entity.RolePermissionOverride;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link RolePermissionOverride} (D1 / F0.5, QA plan 2026-07-02).
 *
 * <h3>Soft-delete contract</h3>
 * Hibernate's {@code @SQLRestriction("deleted = false")} on
 * {@link com.edushift.shared.domain.BaseEntity} filters out
 * soft-deleted rows from JPA-managed queries automatically. Soft-deletion
 * + re-insert is implemented transactionally in
 * {@code PermissionOverrideService.upsert}, not here.
 */
@Repository
public interface RolePermissionOverrideRepository
		extends JpaRepository<RolePermissionOverride, UUID> {

	/**
	 * All ACTIVE overrides for a tenant. Used to materialise the
	 * {@code Map<UserRole, Set<String>>} that
	 * {@code LmsRoleAuthorityMapper.snapshotFor} returns.
	 */
	List<RolePermissionOverride> findByTenantId(UUID tenantId);

	/** Active overrides for a single role within a tenant. */
	List<RolePermissionOverride> findByTenantIdAndRole(UUID tenantId, UserRole role);

	/**
	 * Active override for the (tenant, role, authority) triple.
	 * Returns empty if the tenant never customised this authority.
	 */
	Optional<RolePermissionOverride> findByTenantIdAndRoleAndAuthority(
			UUID tenantId, UserRole role, String authority);
}
