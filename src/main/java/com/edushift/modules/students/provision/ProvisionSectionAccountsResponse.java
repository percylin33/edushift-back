package com.edushift.modules.students.provision;

import java.util.List;
import java.util.UUID;

/**
 * Immediate response after mass provision. Credentials are also stored briefly
 * under {@code jobPublicUuid} for Excel re-download (TTL ~20 min).
 */
public record ProvisionSectionAccountsResponse(
		UUID jobPublicUuid,
		UUID sectionPublicUuid,
		String sectionName,
		int studentsProvisioned,
		int guardiansProvisioned,
		int skippedAlreadyLinked,
		int syntheticEmails,
		int realEmails,
		List<ProvisionCredentialRow> credentials
) {}
