package com.edushift.modules.auth.dto;

import com.edushift.shared.validation.annotations.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * CRED-7 first-login submit: password change + light profile (role-aware).
 */
public record CompleteOnboardingRequest(
		@NotBlank @ValidPassword String newPassword,
		@Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@Size(max = 32) String phone,
		@Email @Size(max = 254) String email,
		LocalDate birthDate,
		@Size(max = 500) String address
) {}
