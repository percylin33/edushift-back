package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.RelationshipType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /v1/students/{publicUuid}/guardians}.
 *
 * <h3>"Find or create" semantics</h3>
 * The service first looks up an existing guardian by
 * {@code (documentType, documentNumber)} in the current tenant. If it
 * finds one, the link is created against it (sibling-sharing happy
 * path); if not, a new guardian is created from the profile fields.
 * That's why the profile fields ({@code firstName}, {@code lastName},
 * etc.) are required: an admin who only knows the document number
 * still has to commit to a name in case the row needs to be created.
 */
public record AddGuardianRequest(
		@NotNull(message = "documentType is required")
		DocumentType documentType,

		@NotBlank(message = "documentNumber is required")
		@Size(min = 4, max = 20, message = "documentNumber length out of range")
		@Pattern(regexp = "^[A-Za-z0-9-]+$",
				message = "documentNumber must contain only letters, digits, and dashes")
		String documentNumber,

		@NotBlank(message = "firstName is required")
		@Size(min = 1, max = 100, message = "firstName length out of range")
		String firstName,

		@NotBlank(message = "lastName is required")
		@Size(min = 1, max = 100, message = "lastName length out of range")
		String lastName,

		@Email(message = "email must be a valid address")
		@Size(max = 254, message = "email must be at most 254 characters")
		String email,

		@Size(max = 32, message = "phone must be at most 32 characters")
		String phone,

		@Size(max = 100, message = "occupation must be at most 100 characters")
		String occupation,

		@NotNull(message = "relationship is required")
		RelationshipType relationship,

		boolean isPrimaryContact,
		boolean canPickupStudent
) {
}
