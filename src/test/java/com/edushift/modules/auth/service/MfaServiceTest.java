package com.edushift.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.UnauthorizedException;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link MfaService} (Sprint 17 / BE-17.2).
 *
 * <p>Mock-based; the real TOTP library is exercised indirectly through
 * {@link TotpService}, which is also mocked. The {@link PasswordEncoder}
 * is a real {@link BCryptPasswordEncoder} so the recovery code hashing
 * is end-to-end.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaServiceTest {

	@Mock private UserRepository userRepository;
	@Mock private TenantRepository tenantRepository;
	@Mock private TotpService totpService;

	private MfaService mfaService;
	private PasswordEncoder passwordEncoder;
	private RecoveryCodeService recoveryCodeService;

	@BeforeEach
	void setUp() {
		passwordEncoder = new BCryptPasswordEncoder();
		recoveryCodeService = new RecoveryCodeService(passwordEncoder);
		mfaService = new MfaService(userRepository, tenantRepository,
				totpService, recoveryCodeService, passwordEncoder);
	}

	// ====================================================================
	// startEnrollment
	// ====================================================================

	@Nested
	@DisplayName("startEnrollment")
	class StartEnrollment {

		@Test
		@DisplayName("Returns secret + QR + otpauth URI for MFA-disabled user")
		void happyPath() {
			User user = newUser(false);
			Tenant tenant = newTenant();
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			when(tenantRepository.findById(user.getTenantId())).thenReturn(Optional.of(tenant));
			GoogleAuthenticatorKey key = stubKey();
			when(totpService.generateSecret()).thenReturn(key);
			when(totpService.buildOtpAuthUri(eq(key), eq(user.getEmail()), eq(tenant.getName())))
					.thenReturn("otpauth://totp/...");
			when(totpService.buildQrCodeDataUrl(eq(key), eq(user.getEmail()), eq(tenant.getName())))
					.thenReturn("otpauth://totp/...");

			var start = mfaService.startEnrollment(user.getPublicUuid());

			assertThat(start.secretBase32()).isEqualTo("JBSWY3DPEHPK3PXP");
			assertThat(start.otpauthUri()).isEqualTo("otpauth://totp/...");
			assertThat(start.qrCodeDataUrl()).isEqualTo("otpauth://totp/...");
		}

		@Test
		@DisplayName("MFA_ALREADY_ENABLED when user already has MFA on")
		void alreadyEnabled() {
			User user = newUser(true);
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));

			assertThatThrownBy(() -> mfaService.startEnrollment(user.getPublicUuid()))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo("MFA_ALREADY_ENABLED");
		}
	}

	// ====================================================================
	// verifyEnrollment
	// ====================================================================

	@Nested
	@DisplayName("verifyEnrollment")
	class VerifyEnrollment {

		@Test
		@DisplayName("INVALID_TOTP_CODE when code doesn't match")
		void invalidCode() {
			User user = newUser(false);
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			when(totpService.validateCode(any(), anyInt())).thenReturn(false);

			assertThatThrownBy(() -> mfaService.verifyEnrollment(
					user.getPublicUuid(), "secret", 123456))
					.isInstanceOf(UnauthorizedException.class)
					.extracting("code").isEqualTo("INVALID_TOTP_CODE");
		}

		@Test
		@DisplayName("Persists secret + recovery codes + flips mfaEnabled")
		void happyPath() {
			User user = newUser(false);
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			when(totpService.validateCode(any(), anyInt())).thenReturn(true);

			List<String> codes = mfaService.verifyEnrollment(
					user.getPublicUuid(), "JBSWY3DPEHPK3PXP", 654321);

			assertThat(codes).hasSize(RecoveryCodeService.RECOVERY_CODE_COUNT);
			assertThat(user.isMfaEnabled()).isTrue();
			assertThat(user.getMfaSecretHash()).isEqualTo("JBSWY3DPEHPK3PXP");
			assertThat(user.getMfaRecoveryCodesHash()).hasSize(RecoveryCodeService.RECOVERY_CODE_COUNT);
			assertThat(user.getMfaEnrolledAt()).isNotNull();
			verify(userRepository, times(1)).saveAndFlush(user);
		}
	}

	// ====================================================================
	// verifyChallenge
	// ====================================================================

	@Nested
	@DisplayName("verifyChallenge")
	class VerifyChallenge {

		@Test
		@DisplayName("Valid TOTP code → RESULT_OK")
		void validTotp() {
			User user = newUser(true);
			user.setMfaSecretHash("JBSWY3DPEHPK3PXP");
			user.setMfaRecoveryCodesHash(new ArrayList<>());
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			when(totpService.validateCode("JBSWY3DPEHPK3PXP", 123456)).thenReturn(true);

			String result = mfaService.verifyChallenge(user.getPublicUuid(), "123456");

			assertThat(result).isEqualTo("OK");
		}

		@Test
		@DisplayName("Valid recovery code → RESULT_OK + code consumed")
		void validRecovery() {
			User user = newUser(true);
			user.setMfaSecretHash("JBSWY3DPEHPK3PXP");
			// The service normalizes codes by stripping the dash; we encode
			// the normalized form so the match succeeds.
			String hashed = passwordEncoder.encode("ABCDEFGHIJ");
			user.setMfaRecoveryCodesHash(new ArrayList<>(List.of(hashed)));
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));

			String result = mfaService.verifyChallenge(user.getPublicUuid(), "ABCDE-FGHIJ");

			assertThat(result).isEqualTo("OK");
			// The code was consumed (replaced by null)
			assertThat(user.getMfaRecoveryCodesHash().get(0)).isNull();
			verify(userRepository, times(1)).saveAndFlush(user);
		}

		@Test
		@DisplayName("Invalid code → INVALID_MFA_CODE")
		void invalidCode() {
			User user = newUser(true);
			user.setMfaSecretHash("JBSWY3DPEHPK3PXP");
			user.setMfaRecoveryCodesHash(new ArrayList<>());
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			when(totpService.validateCode(any(), anyInt())).thenReturn(false);

			assertThatThrownBy(() -> mfaService.verifyChallenge(user.getPublicUuid(), "999999"))
					.isInstanceOf(UnauthorizedException.class)
					.extracting("code").isEqualTo("INVALID_TOTP_CODE");
		}

		@Test
		@DisplayName("MFA not enabled on user → MFA_NOT_ENABLED")
		void notEnabled() {
			User user = newUser(false);
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));

			assertThatThrownBy(() -> mfaService.verifyChallenge(user.getPublicUuid(), "123456"))
					.isInstanceOf(UnauthorizedException.class)
					.extracting("code").isEqualTo("MFA_NOT_ENABLED");
		}
	}

	// ====================================================================
	// disable
	// ====================================================================

	@Nested
	@DisplayName("disable")
	class Disable {

		@Test
		@DisplayName("Clears all MFA state after proving password + TOTP")
		void happyPath() {
			User user = newUser(true);
			user.setMfaSecretHash("JBSWY3DPEHPK3PXP");
			user.setMfaEnrolledAt(java.time.Instant.now());
			user.setMfaRecoveryCodesHash(new ArrayList<>());
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			when(totpService.validateCode("JBSWY3DPEHPK3PXP", 123456)).thenReturn(true);

			mfaService.disable(user.getPublicUuid(), "currentPassword", "123456");

			assertThat(user.isMfaEnabled()).isFalse();
			assertThat(user.getMfaSecretHash()).isNull();
			assertThat(user.getMfaEnrolledAt()).isNull();
			assertThat(user.getMfaRecoveryCodesHash()).isNull();
		}

		@Test
		@DisplayName("Wrong password → INVALID_PASSWORD")
		void wrongPassword() {
			User user = newUser(true);
			user.setPasswordHash(passwordEncoder.encode("correct"));
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));

			assertThatThrownBy(() -> mfaService.disable(user.getPublicUuid(), "wrong", "123456"))
					.isInstanceOf(UnauthorizedException.class)
					.extracting("code").isEqualTo("INVALID_PASSWORD");
		}
	}

	// ====================================================================
	// regenerateRecoveryCodes
	// ====================================================================

	@Nested
	@DisplayName("regenerateRecoveryCodes")
	class Regenerate {

		@Test
		@DisplayName("Issues a fresh set of 10 codes")
		void happyPath() {
			User user = newUser(true);
			user.setPasswordHash(passwordEncoder.encode("current"));
			user.setMfaRecoveryCodesHash(new ArrayList<>());
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));

			List<String> codes = mfaService.regenerateRecoveryCodes(user.getPublicUuid(), "current");

			assertThat(codes).hasSize(RecoveryCodeService.RECOVERY_CODE_COUNT);
			assertThat(user.getMfaRecoveryCodesHash()).hasSize(RecoveryCodeService.RECOVERY_CODE_COUNT);
		}
	}

	// ====================================================================
	// helpers
	// ====================================================================

	private Tenant newTenant() {
		Tenant t = new Tenant();
		t.setId(UUID.randomUUID());
		t.setSlug("acme");
		t.setName("Acme School");
		t.setStatus(TenantStatus.ACTIVE);
		return t;
	}

	private User newUser(boolean mfaEnabled) {
		User u = new User();
		u.setId(UUID.randomUUID());
		u.setPublicUuid(UUID.randomUUID());
		u.setEmail("alice@acme.test");
		u.setStatus(UserStatus.ACTIVE);
		u.setTenantId(UUID.randomUUID());
		u.setMfaEnabled(mfaEnabled);
		// Seed a known password hash for tests that exercise disable()
		u.setPasswordHash(passwordEncoder.encode("currentPassword"));
		return u;
	}

	private GoogleAuthenticatorKey stubKey() {
		// Manually construct a key with a known base32 secret
		return new GoogleAuthenticatorKey.Builder("JBSWY3DPEHPK3PXP").build();
	}

	private static int anyInt() {
		return org.mockito.ArgumentMatchers.anyInt();
	}
}