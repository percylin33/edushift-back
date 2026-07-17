package com.edushift.modules.admin.superadmin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 15 / F-06 / H-04: server-side view of a SUPER_ADMIN account.
 * Sensitive fields (password hash, mfa secret, recovery codes) are
 * always excluded — the contract is additive only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuperAdminSummary(
		UUID publicUuid,
		String email,
		String firstName,
		String lastName,
		String status,
		boolean mfaEnabled,
		Instant lastLoginAt,
		Instant createdAt,
		List<String> roles
) {}
