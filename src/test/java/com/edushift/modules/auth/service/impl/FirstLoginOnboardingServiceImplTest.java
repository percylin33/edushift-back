package com.edushift.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.edushift.modules.auth.dto.CompleteOnboardingRequest;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.mapper.UserMapper;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class FirstLoginOnboardingServiceImplTest {

	@Mock UserRepository userRepository;
	@Mock StudentRepository studentRepository;
	@Mock GuardianRepository guardianRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock UserMapper userMapper;
	@InjectMocks FirstLoginOnboardingServiceImpl service;

	private User user;
	private final UUID publicUuid = UUID.randomUUID();
	private final CompleteOnboardingRequest request = new CompleteOnboardingRequest(
			"NewPersonal2026!", null, null, null, null, null, null);

	@BeforeEach
	void setUp() {
		user = new User();
		user.setPublicUuid(publicUuid);
		user.setFirstName("First");
		user.setLastName("Last");
		user.setPhone("999999999");
		user.setPasswordHash("old-hash");
		user.setMustChangePassword(true);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(publicUuid.toString(), null, java.util.List.of()));
		lenient().when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));
	}

	@AfterEach
	void tearDown() { SecurityContextHolder.clearContext(); }

	@ParameterizedTest
	@EnumSource(value = UserRole.class, names = {"STUDENT", "PARENT", "TEACHER", "TENANT_ADMIN"})
	void completesWithoutAskingForTemporaryPassword(UserRole role) {
		user.addRole(role);
		when(passwordEncoder.encode(request.newPassword())).thenReturn("new-hash");
		when(userRepository.saveAndFlush(user)).thenReturn(user);
		service.completeOnboarding(request);
		assertThat(user.getPasswordHash()).isEqualTo("new-hash");
		assertThat(user.isMustChangePassword()).isFalse();
		assertThatThrownBy(() -> service.completeOnboarding(request))
				.hasMessageContaining("initial password change");
		verify(userRepository, times(1)).saveAndFlush(user);
	}

	@Test
	void rejectsReusingTemporaryPassword() {
		when(passwordEncoder.matches(request.newPassword(), "old-hash")).thenReturn(true);
		assertThatThrownBy(() -> service.completeOnboarding(request))
				.hasMessageContaining("different");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void requiresAuthenticatedSession() {
		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> service.completeOnboarding(request))
				.hasMessageContaining("Authentication required");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void rejectsUserMissingFromAuthenticatedTenant() {
		when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.completeOnboarding(request))
				.hasMessageContaining("no longer exists");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void requiredFieldsExcludeTemporaryPassword() {
		assertThat(service.getOnboarding().requiredFields()).contains("newPassword")
				.doesNotContain("currentPassword");
	}
}
