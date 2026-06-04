package com.edushift.modules.tenants.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Partial update for the current tenant ({@code PATCH /v1/tenants/me}).
 *
 * <h3>Semantics: partial-flat (null = "leave as-is")</h3>
 * Every field is nullable. The service applies a field-by-field merge:
 * when the value comes in as {@code null} the existing column is
 * preserved; when it comes in as a non-null value the column is
 * overwritten. {@code branding} merges at the field level
 * ({@code BrandingDto}'s own nullable members let the front update one
 * color without resending the rest of the object), while
 * {@code settings} and {@code featureFlags} are <strong>replaced
 * wholesale</strong> when present — those are opaque bags whose
 * meaning the back can't reason about safely.
 *
 * <h3>Why this isn't RFC 7396 (JSON Merge Patch)</h3>
 * Merge Patch uses literal {@code null} as "delete this key", which
 * collides with the "leave as-is" semantics admins actually expect on
 * a partial form. We keep things simple and pragmatic: send only what
 * you want to change.
 *
 * <h3>What you can NOT change here</h3>
 * <ul>
 *   <li>{@code slug} — would break URLs, third-party integrations,
 *       and the partial unique index logic. A future "rename" endpoint
 *       can handle it transactionally.</li>
 *   <li>{@code status}, {@code plan} — lifecycle / billing transitions
 *       have their own endpoints (e.g. {@code POST /tenants/me/activate}
 *       in BE-2.3) so each change has a clean audit trail.</li>
 * </ul>
 */
public record UpdateTenantRequest(

		@Size(min = 2, max = 200, message = "name must be between 2 and 200 characters")
		String name,

		@Size(max = 200, message = "customDomain must not exceed 200 characters")
		@Pattern(
				regexp = "^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$",
				message = "customDomain must look like a hostname (lowercase, dots, dashes)"
		)
		String customDomain,

		@Valid
		BrandingDto branding,

		Map<String, Object> settings,

		Map<String, Object> featureFlags,

		@Positive(message = "maxStudents must be greater than zero")
		Integer maxStudents,

		@Positive(message = "maxTeachers must be greater than zero")
		Integer maxTeachers
) {
}
