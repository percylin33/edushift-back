package com.edushift.modules.tenants.dto;

import com.edushift.shared.security.StaffAccessCatalog.ProfileCode;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** Full replacement of one staff user's selected profiles and exceptions. */
public record ReplaceStaffAccessRequest(
		@NotNull List<ProfileCode> profiles,
		@NotNull Map<String, Boolean> overrides
) {
}
