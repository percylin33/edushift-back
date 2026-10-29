package com.edushift.modules.admin.tenants.dto;

import jakarta.validation.constraints.NotNull;

public record AdminTenantAiSettingsRequest(
		@NotNull Boolean enabled
) {}
