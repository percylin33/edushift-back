package com.edushift.modules.disclosures.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.time.LocalDate;

/** Tenant-owned file linked to an annual disclosure. */
public record DisclosureAttachment(
		@NotNull DisclosureAttachmentCategory category,
		@NotBlank @Size(max = 120) String label,
		@NotNull UUID filePublicUuid,
		@Size(max = 80) String versionLabel,
		LocalDate documentDate,
		LocalDate validUntil,
		Boolean visibleToFamilies
) {
	public DisclosureAttachment {
		// Old saved packages had no visibility flag; preserve their published behavior.
		if (visibleToFamilies == null) visibleToFamilies = true;
	}

	public DisclosureAttachment(DisclosureAttachmentCategory category, String label, UUID filePublicUuid) {
		this(category, label, filePublicUuid, null, null, null, true);
	}
}
