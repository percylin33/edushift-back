package com.edushift.modules.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.infrastructure.ratelimit.SimpleRateLimiter;
import com.edushift.modules.admin.auth.dto.AdminInitialPasswordChangeRequest;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AdminInitialPasswordChangeTest {

	@Mock UserRepository userRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock JwtService jwtService;
	@Mock AuditLogger auditLogger;
	@Mock SimpleRateLimiter rateLimiter;
	@Mock PlatformTransactionManager txManager;
	@Mock RefreshTokenRepository refreshTokenRepository;
	@InjectMocks AdminAuthService service;
	private User user;
	private final UUID publicUuid = UUID.randomUUID();
	private final AdminInitialPasswordChangeRequest request =
			new AdminInitialPasswordChangeRequest("NewPersonal2026!");

	@BeforeEach
	void setUp() {
		user = new User();
		user.setPublicUuid(publicUuid);
		user.setStatus(UserStatus.ACTIVE);
		user.addRole(UserRole.SUPER_ADMIN);
		user.setMustChangePassword(true);
		user.setPasswordHash("old-hash");
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		lenient().when(userRepository.findByPublicUuid(publicUuid)).thenAnswer(invocation -> {
			assertThat(TenantContext.currentRequired()).isEqualTo(TenantIdResolver.SUPER_ADMIN_SENTINEL);
			return Optional.of(user);
		});
		lenient().when(jwtService.parseAndValidate("onboarding")).thenReturn(
				claims(JwtService.TokenType.ONBOARDING, TenantIdResolver.SUPER_ADMIN_SENTINEL));
	}

	private JwtService.JwtClaims claims(JwtService.TokenType type, UUID tenantId) {
		return new JwtService.JwtClaims(publicUuid.toString(), tenantId, "edushift-system",
				Set.of("SUPER_ADMIN"), type, UUID.randomUUID(), Instant.now(),
				Instant.now().plusSeconds(300), null);
	}

	@Test
	void changesPasswordWithOnboardingTokenAndRejectsReplay() {
		when(passwordEncoder.encode(request.newPassword())).thenReturn("new-hash");
		service.changeInitialPassword("onboarding", request, "127.0.0.1");
		assertThat(user.getPasswordHash()).isEqualTo("new-hash");
		assertThat(user.isMustChangePassword()).isFalse();
		assertThatThrownBy(() -> service.changeInitialPassword("onboarding", request, "127.0.0.1"))
				.hasMessageContaining("initial password change");
		verify(userRepository, times(1)).saveAndFlush(user);
	}

	@Test
	void rejectsSamePassword() {
		when(passwordEncoder.matches(request.newPassword(), "old-hash")).thenReturn(true);
		assertThatThrownBy(() -> service.changeInitialPassword("onboarding", request, "127.0.0.1"))
				.hasMessageContaining("differ");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void rejectsTokenFromAnotherTenant() {
		when(jwtService.parseAndValidate("other")).thenReturn(claims(JwtService.TokenType.ONBOARDING, UUID.randomUUID()));
		assertThatThrownBy(() -> service.changeInitialPassword("other", request, "127.0.0.1"))
				.hasMessageContaining("onboarding token");
		verifyNoInteractions(userRepository);
	}

	@Test
	void rejectsAccessToken() {
		when(jwtService.parseAndValidate("access")).thenReturn(claims(JwtService.TokenType.ACCESS, TenantIdResolver.SUPER_ADMIN_SENTINEL));
		assertThatThrownBy(() -> service.changeInitialPassword("access", request, "127.0.0.1"))
				.hasMessageContaining("onboarding token");
	}

	@Test
	void rejectsExpiredOrInvalidToken() {
		when(jwtService.parseAndValidate("expired")).thenThrow(new UnauthorizedException("TOKEN_EXPIRED", "Token expired"));
		assertThatThrownBy(() -> service.changeInitialPassword("expired", request, "127.0.0.1"))
				.hasMessageContaining("expired");
		verifyNoInteractions(userRepository);
	}

	@Test
	void rejectsNonSuperAdmin() {
		user.setRoleSet(Set.of(UserRole.STUDENT));
		assertThatThrownBy(() -> service.changeInitialPassword("onboarding", request, "127.0.0.1"))
				.hasMessageContaining("initial password change");
		verify(userRepository, never()).saveAndFlush(any());
	}
}
