package com.edushift.modules.admin.tenants.dto;

public record AdminTenantAiSettingsResponse(
		boolean configured,
		boolean enabled,
		boolean planAvailable,
		String planCode,
		int dailyRequestLimit,
		long monthlyTokenLimit,
		String blockedReason
) {}
