package com.edushift.shared.validation.groups;

/**
 * Validation group: applied on PATCH / partial-update operations where most
 * fields are optional but, when present, must still be well-formed.
 * <p>
 * Typically pair with {@code @NotNull(groups = OnPatch.class)} only on the
 * key/identity fields, and put format constraints in {@code Default.class}.
 */
public interface OnPatch {
}
