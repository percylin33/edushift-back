package com.edushift.modules.tenants.dto;

import com.edushift.shared.security.StaffAccessCatalog.PermissionOption;
import com.edushift.shared.security.StaffAccessCatalog.ProfileCode;
import com.edushift.shared.security.StaffAccessCatalog.ProfileOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Permission assignment and resolved access shown in the user detail screen. */
public record StaffAccessState(
		Set<ProfileCode> profiles,
		Map<String, Boolean> overrides,
		Set<String> baseAuthorities,
		Set<String> effectiveAuthorities,
		List<ProfileOption> availableProfiles,
		List<PermissionOption> permissionCatalog
) {
}
