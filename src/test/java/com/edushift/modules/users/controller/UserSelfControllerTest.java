package com.edushift.modules.users.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.exception.FileNotFoundException;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice tests for {@link UserSelfController} (Sprint 17 / BE-17.3).
 *
 * <p>Covers the avatar upload / delete flow:
 * <ul>
 *   <li>Upload stores via {@link FileObjectService}, updates
 *       {@code users.avatar_url}, and drops the previous avatar.</li>
 *   <li>Delete clears the column and removes the file. Idempotent.</li>
 *   <li>Multi-tenant safety: the userId is read from the security
 *       principal, never from the request body.</li>
 * </ul>
 *
 * <h3>URL prefix</h3>
 * {@code WebConfiguration} adds a {@code /v1} path prefix to every
 * controller in {@code com.edushift.modules.*.controller}, so the
 * full path is {@code /v1/users/me/avatar}.
 */
@WebMvcTest(
		controllers = UserSelfController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class UserSelfControllerTest {

	@Autowired private MockMvc mockMvc;

	@MockitoBean private FileObjectService fileObjectService;
	@MockitoBean private UserRepository userRepository;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private LmsRoleAuthorityMapper roleAuthorityMapper;
	@MockitoBean private CurrentUserProvider currentUserProvider;

	private static final UUID TENANT_ID = UUID.randomUUID();
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID USER_PUBLIC_UUID = UUID.randomUUID();
	private static final UUID OLD_AVATAR_UUID = UUID.randomUUID();
	private static final UUID NEW_AVATAR_UUID = UUID.randomUUID();

	@BeforeEach
	void stubAuth() {
		given(currentUserProvider.currentTenantId()).willReturn(Optional.of(TENANT_ID));
		given(currentUserProvider.currentUserId()).willReturn(Optional.of(USER_ID));
	}

	@Nested
	@DisplayName("POST /v1/users/me/avatar")
	class UploadAvatar {

		@Test
		@DisplayName("201 + FileObjectResponse when the upload succeeds")
		void happyPath() throws Exception {
			User user = newUser(null); // no previous avatar
			givenUser(user);

			MockMultipartFile png = new MockMultipartFile(
					"file", "avatar.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
			FileObject stored = newFileObject();
			given(fileObjectService.store(eq(TENANT_ID), eq("avatars"), any())).willReturn(stored);

			mockMvc.perform(multipart("/v1/users/me/avatar")
							.file(png)
							.with(csrf())
							.with(authentication(auth())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.publicUuid").value(NEW_AVATAR_UUID.toString()))
					.andExpect(jsonPath("$.data.contentType").value("image/png"));

			// users.avatar_url updated to the new file's publicUuid
			ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
			verify(userRepository, times(1)).save(userCap.capture());
			assertThat(userCap.getValue().getAvatarUrl()).isEqualTo(NEW_AVATAR_UUID.toString());
			// No previous avatar → no delete call
			verify(fileObjectService, never()).delete(any(UUID.class));
		}

		@Test
		@DisplayName("Replacing an existing avatar also deletes the old one")
		void replacesPreviousAvatar() throws Exception {
			User user = newUser(OLD_AVATAR_UUID);
			givenUser(user);

			MockMultipartFile png = new MockMultipartFile(
					"file", "avatar2.png", "image/png", new byte[]{1, 2, 3, 4});
			FileObject stored = newFileObject();
			given(fileObjectService.store(eq(TENANT_ID), eq("avatars"), any())).willReturn(stored);

			mockMvc.perform(multipart("/v1/users/me/avatar")
							.file(png)
							.with(csrf())
							.with(authentication(auth())))
					.andExpect(status().isCreated());

			// The previous avatar is dropped
			verify(fileObjectService, times(1)).delete(OLD_AVATAR_UUID);
		}

		@Test
		@DisplayName("Failure to delete the previous avatar is logged but does not fail the request")
		void previousDeleteFailureIsSwallowed() throws Exception {
			User user = newUser(OLD_AVATAR_UUID);
			givenUser(user);

			MockMultipartFile png = new MockMultipartFile(
					"file", "avatar.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
			FileObject stored = newFileObject();
			given(fileObjectService.store(eq(TENANT_ID), eq("avatars"), any())).willReturn(stored);
			doThrow(new FileNotFoundException(OLD_AVATAR_UUID.toString()))
					.when(fileObjectService).delete(OLD_AVATAR_UUID);

			mockMvc.perform(multipart("/v1/users/me/avatar")
							.file(png)
							.with(csrf())
							.with(authentication(auth())))
					.andExpect(status().isCreated());
			// The new avatar is in place; the controller logged the warn
			// and moved on. The user gets a successful response.
			ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
			verify(userRepository, times(1)).save(userCap.capture());
			assertThat(userCap.getValue().getAvatarUrl()).isEqualTo(NEW_AVATAR_UUID.toString());
		}

		@Test
		@DisplayName("401 when no authentication is present")
		void unauthenticated() throws Exception {
			MockMultipartFile png = new MockMultipartFile(
					"file", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});
			mockMvc.perform(multipart("/v1/users/me/avatar").file(png).with(csrf()))
					.andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("DELETE /v1/users/me/avatar")
	class DeleteAvatar {

		@Test
		@DisplayName("204 + column cleared + file deleted")
		void happyPath() throws Exception {
			User user = newUser(OLD_AVATAR_UUID);
			givenUser(user);

			mockMvc.perform(delete("/v1/users/me/avatar")
							.with(csrf())
							.with(authentication(auth())))
					.andExpect(status().isNoContent());

			ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
			verify(userRepository, times(1)).save(userCap.capture());
			assertThat(userCap.getValue().getAvatarUrl()).isNull();
			verify(fileObjectService, times(1)).delete(OLD_AVATAR_UUID);
		}

		@Test
		@DisplayName("401 when no authentication is present")
		void unauthenticated() throws Exception {
			mockMvc.perform(delete("/v1/users/me/avatar").with(csrf()))
					.andExpect(status().isUnauthorized());
		}
	}

	// ====================================================================
	// helpers
	// ====================================================================

	private static JwtAuthenticationToken auth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				USER_PUBLIC_UUID, TENANT_ID, "acme", "alice@acme.test");
		return new JwtAuthenticationToken(principal, "fake.token",
				List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private void givenUser(User user) {
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
	}

	private static User newUser(UUID avatarUuid) {
		User u = new User();
		u.setId(USER_ID);
		u.setPublicUuid(USER_PUBLIC_UUID);
		u.setEmail("alice@acme.test");
		u.setStatus(UserStatus.ACTIVE);
		u.setTenantId(TENANT_ID);
		u.setAvatarUrl(avatarUuid == null ? null : avatarUuid.toString());
		return u;
	}

	private static FileObject newFileObject() {
		FileObject f = new FileObject();
		f.setPublicUuid(NEW_AVATAR_UUID);
		f.setRemoteKey("tenants/" + TENANT_ID + "/avatars/" + NEW_AVATAR_UUID);
		f.setOriginalName("avatar.png");
		f.setContentType("image/png");
		f.setSizeBytes(4L);
		f.setCreatedAt(Instant.now());
		return f;
	}
}