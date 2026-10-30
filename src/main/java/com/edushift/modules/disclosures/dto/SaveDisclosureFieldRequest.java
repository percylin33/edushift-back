package com.edushift.modules.disclosures.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Saves one step while retaining the package's linked documents. */
public record SaveDisclosureFieldRequest(
		@NotNull @Valid DisclosureField field,
		@Size(max = 20) List<@NotNull @Valid DisclosureAttachment> attachments
) {
	public SaveDisclosureFieldRequest {
		attachments = attachments == null ? List.of() : List.copyOf(attachments);
	}
}
