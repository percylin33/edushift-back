package com.edushift.modules.students.provision;

import jakarta.validation.constraints.NotNull;

/**
 * Options for {@code POST .../sections/{uuid}/provision-accounts} (CRED-7).
 */
public record ProvisionSectionAccountsRequest(
		@NotNull Boolean includeGuardians,
		@NotNull Boolean overwriteMissingEmailWithSynthetic
) {
	public boolean includeGuardiansOrDefault() {
		return includeGuardians == null || includeGuardians;
	}

	public boolean useSyntheticWhenMissingOrDefault() {
		return overwriteMissingEmailWithSynthetic == null || overwriteMissingEmailWithSynthetic;
	}
}
