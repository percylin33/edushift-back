package com.edushift.modules.tenants.repository;

import com.edushift.modules.tenants.entity.StaffUserPermissionOverride;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserPermissionOverrideRepository
		extends JpaRepository<StaffUserPermissionOverride, UUID> {

	List<StaffUserPermissionOverride> findByTenantIdAndUserIdAndDeletedFalse(
			UUID tenantId, UUID userId);

	Optional<StaffUserPermissionOverride> findByTenantIdAndUserIdAndAuthority(
			UUID tenantId, UUID userId, String authority);
}
