package com.edushift.modules.auth.dto;

import com.edushift.shared.validation.annotations.PhoneNumber;
import com.edushift.shared.validation.annotations.SafeText;
import com.edushift.shared.validation.annotations.ValidEmail;
import com.edushift.shared.validation.annotations.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Payload for creating a new {@link com.edushift.modules.auth.entity.User}.
 * <p>
 * The raw password travels here exactly once — the service layer hashes it
 * immediately and stores only the digest. This record never round-trips to
 * the client.
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>{@code firstName} / {@code lastName}: required, max 100 chars,
 *       free of HTML / control characters ({@link SafeText}).</li>
 *   <li>{@code email}: required and well-formed ({@link ValidEmail}).</li>
 *   <li>{@code password}: strong policy via {@link ValidPassword} — kept off
 *       the entity so the hash never accidentally lives in a DTO.</li>
 *   <li>{@code phone}: optional, E.164-ish format ({@link PhoneNumber}).</li>
 *   <li>{@code avatarUrl}: optional, must be a valid URL when present.</li>
 * </ul>
 */
public record CreateUserRequest(

		@NotBlank
		@Size(max = 100)
		@SafeText
		String firstName,

		@NotBlank
		@Size(max = 100)
		@SafeText
		String lastName,

		@ValidEmail
		String email,

		@ValidPassword
		String password,

		@PhoneNumber
		@Size(max = 32)
		String phone,

		@URL
		@Size(max = 512)
		String avatarUrl
) {
}
