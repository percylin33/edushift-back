package com.edushift.modules.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tenants.dto.RegisterTenantRequest;
import com.edushift.modules.users.dto.AcceptInvitationRequest;
import com.edushift.shared.validation.validators.StrongPasswordValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bean-validation tests for the {@code @ValidPassword} policy as it applies
 * to the public onboarding DTOs ({@link AcceptInvitationRequest},
 * {@link RegisterTenantRequest}).
 *
 * <p>Closes DEBT-USR-2: prior to this sprint, those DTOs enforced only
 * {@code @Size(min=8)} on the password fields, allowing trivial passwords
 * like {@code "12345678"} on invitation-accept or self-signup. The strong
 * policy (8-72 chars, mixed case + digit + special) must now reject those
 * values uniformly.
 */
class PasswordPolicyValidationTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeFactory() {
		if (factory != null) {
			factory.close();
		}
	}

	// =====================================================================
	// AcceptInvitationRequest
	// =====================================================================

	@Test
	@DisplayName("AcceptInvitation: 8-digit numeric-only password is REJECTED "
			+ "(was the pre-sprint gap — only @Size(min=8) applied)")
	void acceptInvitationRejectsTrivialPasswords() {
		// "12345678" — length ok, but no upper/lower/special
		AcceptInvitationRequest body = new AcceptInvitationRequest(
				"valid-token-of-sufficient-length-aaaaaaa", "12345678");

		Set<? extends jakarta.validation.ConstraintViolation<?>> violations =
				validator.validate(body);

		assertThat(violations).isNotEmpty();
		assertThat(violations)
				.anyMatch(v -> v.getMessage().toLowerCase().contains("password"));
	}

	@Test
	@DisplayName("AcceptInvitation: lowercase-only 12-char password is REJECTED")
	void acceptInvitationRejectsLowercaseOnly() {
		AcceptInvitationRequest body = new AcceptInvitationRequest(
				"valid-token-of-sufficient-length-aaaaaaa", "abcdefghijkl");

		Set<? extends jakarta.validation.ConstraintViolation<?>> violations =
				validator.validate(body);

		assertThat(violations).isNotEmpty();
	}

	@Test
	@DisplayName("AcceptInvitation: blank password is REJECTED (@NotBlank from @ValidPassword)")
	void acceptInvitationRejectsBlank() {
		AcceptInvitationRequest body = new AcceptInvitationRequest(
				"valid-token-of-sufficient-length-aaaaaaa", "");

		Set<? extends jakarta.validation.ConstraintViolation<?>> violations =
				validator.validate(body);

		assertThat(violations).isNotEmpty();
	}

	@Test
	@DisplayName("AcceptInvitation: 73-char password is REJECTED (max 72)")
	void acceptInvitationRejectsTooLong() {
		String tooLong = "A1!" + "x".repeat(70);  // 73 chars total
		AcceptInvitationRequest body = new AcceptInvitationRequest(
				"valid-token-of-sufficient-length-aaaaaaa", tooLong);

		Set<? extends jakarta.validation.ConstraintViolation<?>> violations =
				validator.validate(body);

		assertThat(violations).isNotEmpty();
	}

	@Test
	@DisplayName("AcceptInvitation: policy-compliant password is ACCEPTED")
	void acceptInvitationAcceptsStrongPassword() {
		AcceptInvitationRequest body = new AcceptInvitationRequest(
				"valid-token-of-sufficient-length-aaaaaaa", "Sup3rSecret!");

		Set<? extends jakarta.validation.ConstraintViolation<?>> violations =
				validator.validate(body);

		assertThat(violations).isEmpty();
	}

	// =====================================================================
	// RegisterTenantRequest
	// =====================================================================

	@Test
	@DisplayName("RegisterTenant: 8-digit numeric-only password is REJECTED")
	void registerTenantRejectsTrivialPasswords() {
		RegisterTenantRequest body = new RegisterTenantRequest(
				"Acme Corp", "acme-co", "founder@acme.test", "12345678",
				"Founder", "Doe");

		Set<? extends jakarta.validation.ConstraintViolation<?>> violations =
				validator.validate(body);

		assertThat(violations).isNotEmpty();
	}

	@Test
	@DisplayName("RegisterTenant: policy-compliant password is ACCEPTED")
	void registerTenantAcceptsStrongPassword() {
		RegisterTenantRequest body = new RegisterTenantRequest(
				"Acme Corp", "acme-co", "founder@acme.test", "Sup3rSecret!",
				"Founder", "Doe");

		Set<? extends jakarta.validation.ConstraintViolation<?>> violations =
				validator.validate(body);

		assertThat(violations).isEmpty();
	}

	// =====================================================================
	// StrongPasswordValidator — direct unit-level coverage
	// =====================================================================

	@Test
	@DisplayName("StrongPasswordValidator: each rule independently enforced")
	void strongPasswordValidatorRules() {
		StrongPasswordValidator v = new StrongPasswordValidator();
		v.initialize(strongAnno());

		// Length boundary
		assertThat(v.isValid("Aa1!567", null)).isFalse();    // 7 chars
		assertThat(v.isValid("Aa1!5678", null)).isTrue();    // 8 chars (min)
		assertThat(v.isValid("Aa1!56789", null)).isTrue();   // 9 chars
		assertThat(v.isValid("Aa1!" + "x".repeat(68), null)).isTrue();  // 72 chars (max)
		assertThat(v.isValid("Aa1!" + "x".repeat(69), null)).isFalse(); // 73 chars

		// Character class
		assertThat(v.isValid("abcdefgh1!", null)).isFalse();  // no upper
		assertThat(v.isValid("ABCDEFGH1!", null)).isFalse();  // no lower
		assertThat(v.isValid("Abcdefghij", null)).isFalse();  // no digit
		assertThat(v.isValid("Abcdefgh1", null)).isFalse();   // no special

		// Happy path
		assertThat(v.isValid("Abcdefgh1!", null)).isTrue();
	}

	private static com.edushift.shared.validation.annotations.StrongPassword strongAnno() {
		return new com.edushift.shared.validation.annotations.StrongPassword() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return com.edushift.shared.validation.annotations.StrongPassword.class;
			}
			@Override public int minLength() { return 8; }
			@Override public int maxLength() { return 72; }
			@Override public boolean requireUppercase() { return true; }
			@Override public boolean requireLowercase() { return true; }
			@Override public boolean requireDigit() { return true; }
			@Override public boolean requireSpecial() { return true; }
			@Override public String message() { return ""; }
			@Override public Class<?>[] groups() { return new Class[0]; }
			@Override public Class<? extends jakarta.validation.Payload>[] payload() {
				@SuppressWarnings("unchecked")
				Class<? extends jakarta.validation.Payload>[] empty =
						new Class[0];
				return empty;
			}
		};
	}
}
