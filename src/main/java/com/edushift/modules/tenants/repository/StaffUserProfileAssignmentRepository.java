package com.edushift.modules.tenants.repository;

import com.edushift.modules.tenants.entity.StaffUserProfileAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserProfileAssignmentRepository
		extends JpaRepository<StaffUserProfileAssignment, UUID> {

	List<StaffUserProfileAssignment> findByTenantIdAndUserIdAndDeletedFalse(UUID tenantId, UUID userId);

	Optional<StaffUserProfileAssignment> findByTenantIdAndUserIdAndProfileCode(
			UUID tenantId, UUID userId, String profileCode);
}
