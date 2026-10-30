package com.edushift.modules.auth.dto;

import com.edushift.modules.auth.entity.EmailSource;
import java.time.LocalDate;
import java.util.List;

/**
 * CRED-7 first-login payload: current values + which light fields are still required.
 */
public record OnboardingMeResponse(
		String role,
		boolean mustChangePassword,
		EmailSource emailSource,
		boolean emailIsSynthetic,
		String firstName,
		String lastName,
		String phone,
		String email,
		LocalDate birthDate,
		String address,
		List<String> requiredFields,
		List<String> optionalFields
) {}
