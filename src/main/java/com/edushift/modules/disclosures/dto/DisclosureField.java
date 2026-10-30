package com.edushift.modules.disclosures.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Value and applicability state for one legal disclosure requirement. */
public record DisclosureField(
		@NotNull DisclosureFieldState state,
		@Size(max = 4000) String value,
		@Size(max = 1000) String note,
		@Size(max = 10) List<@NotNull @jakarta.validation.Valid DisclosureFieldSource> sources,
		boolean documentOnly,
		boolean systemManaged,
		@Size(max = 160) String systemSource
) {
	public DisclosureField {
		sources = sources == null ? List.of() : List.copyOf(sources);
	}

	public DisclosureField(DisclosureFieldState state, String value, String note) {
		this(state, value, note, List.of(), false, false, null);
	}

	public DisclosureField(DisclosureFieldState state, String value, String note,
			List<DisclosureFieldSource> sources) {
		this(state, value, note, sources, false, false, null);
	}
}
