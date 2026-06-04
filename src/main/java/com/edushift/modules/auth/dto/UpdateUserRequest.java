package com.edushift.modules.auth.dto;

import com.edushift.shared.validation.annotations.PhoneNumber;
import com.edushift.shared.validation.annotations.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Payload for updating mutable profile fields of an existing user.
 * <p>
 * Excludes identity-sensitive fields ({@code email}, {@code password},
 * {@code status}, {@code mfaEnabled}, {@code emailVerified}) which travel
 * through dedicated, separately-authorized endpoints.
 */
public record UpdateUserRequest(

		@NotBlank
		@Size(max = 100)
		@SafeText
		String firstName,

		@NotBlank
		@Size(max = 100)
		@SafeText
		String lastName,

		@PhoneNumber
		@Size(max = 32)
		String phone,

		@URL
		@Size(max = 512)
		String avatarUrl
) {
}
