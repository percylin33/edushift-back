package com.edushift.modules.disclosures.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A manually verified pointer from a disclosed field to its supporting document. */
public record DisclosureFieldSource(
		@NotNull UUID filePublicUuid,
		@Size(max = 80) String pageReference
) { }
