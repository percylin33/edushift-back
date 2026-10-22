package com.edushift.modules.disclosures.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Flexible, versioned content. Field keys and labels come from {@link DisclosureCatalog}. */
public record DisclosureContent(
		@NotNull @Size(max = 40) Map<String, @Valid DisclosureField> fields,
		@Size(max = 20) List<@Valid DisclosureAttachment> attachments,
		LocalDate enrollmentDisclosureDeadline,
		LocalDate yearCloseDisclosureDeadline,
		@Size(max = 5000) String additionalInformation
) {
	public static DisclosureContent empty() {
		return new DisclosureContent(new LinkedHashMap<>(), List.of(), null, null, null);
	}
}
