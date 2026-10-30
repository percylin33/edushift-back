package com.edushift.modules.tenants.service;

import com.edushift.modules.tenants.dto.ReplaceStaffAccessRequest;
import com.edushift.modules.tenants.dto.StaffAccessState;
import java.util.Set;
import java.util.UUID;

/** Resolves and manages composable profiles for individual STAFF users. */
public interface StaffUserAccessService {

	StaffAccessState getForUser(UUID tenantId, UUID publicUserUuid);

	StaffAccessState replaceForUser(
			UUID tenantId, UUID publicUserUuid, ReplaceStaffAccessRequest request);

	Set<String> profileAuthoritiesFor(UUID tenantId, UUID internalUserId);

	java.util.Map<String, Boolean> overridesFor(UUID tenantId, UUID internalUserId);
}
