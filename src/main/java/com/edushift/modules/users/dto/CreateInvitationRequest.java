package com.edushift.modules.users.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.shared.validation.IdentityDocumentFields;
import com.edushift.shared.validation.annotations.ValidIdentityDocument;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;

/**
 * Body of {@code POST /v1/users/invitations}.
 *
 * <p>The recipient's profile fields are required — admins must
 * commit to a name when they invite, both because the preflight UI
 * displays it ("Welcome, {firstName}!") and because nameless accounts
 * are an antipattern in audit logs. Roles are required because an
 * empty-roles user cannot do anything; an admin who wants that should
 * be using {@code disable} instead.</p>
 *
 * <p>{@code metadata} is an optional free-form jsonb side-channel:
 * domain modules (e.g. teachers) attach ids to be reacted on at accept
 * time. The public {@code POST /v1/users/invitations} controller does
 * NOT bind this field — only internal callers may set it.</p>
 *
 * <p>{@code documentType}/{@code documentNumber} are required so the
 * admin commits to a legal identity at invite time — the manual lists
 * document as a basic profile field and nameless/id-less accounts are
 * an antipattern in K-12 audit trails.</p>
 */
@ValidIdentityDocument
public record CreateInvitationRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be a valid address")
		@Size(max = 254, message = "email must be at most 254 characters")
		String email,

		@NotBlank(message = "firstName is required")
		@Size(min = 1, max = 100, message = "firstName must be between 1 and 100 characters")
		String firstName,

		@NotBlank(message = "lastName is required")
		@Size(min = 1, max = 100, message = "lastName must be between 1 and 100 characters")
		String lastName,

		@NotNull(message = "roles must not be null")
		@NotEmpty(message = "roles must contain at least one role")
		Set<String> roles,

		@NotNull(message = "documentType is required")
		DocumentType documentType,

		@NotBlank(message = "documentNumber is required")
		@Size(min = 4, max = 20, message = "documentNumber length out of range")
		@Pattern(regexp = "^[A-Za-z0-9-]+$",
				message = "documentNumber must contain only letters, digits, and dashes")
		String documentNumber,

		/**
		 * Optional. Internal-only side-channel; the public REST
		 * controller binds {@code null} for this field — see
		 * {@code UserInvitationController.create}.
		 */
		Map<String, Object> metadata
) implements IdentityDocumentFields {

	/**
	 * Convenience constructor for the common case where there is no
	 * metadata payload (public API and admin UI).
	 */
	public CreateInvitationRequest(String email, String firstName,
			String lastName, Set<String> roles,
			DocumentType documentType, String documentNumber) {
		this(email, firstName, lastName, roles, documentType, documentNumber, null);
	}
}
