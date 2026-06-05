package com.edushift.modules.tenants.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.UserSummary;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.modules.tenants.dto.BrandingDto;
import com.edushift.modules.tenants.dto.RegisterTenantRequest;
import com.edushift.modules.tenants.dto.TenantResponse;
import com.edushift.modules.tenants.dto.UpdateTenantRequest;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.exception.TenantNotFoundException;
import com.edushift.modules.tenants.mapper.TenantMapper;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit tests for {@link TenantServiceImpl}.
 *
 * <p>The mapper dependency is the <em>real</em> {@link TenantMapper} (no mock):
 * its behavior is already pinned by {@code TenantMapperTest} and using it
 * end-to-end here gives us a more realistic assertion surface (we can
 * read the resulting {@code TenantResponse} fields without writing
 * answer functions).
 *
 * <p>The {@link PlatformTransactionManager} mock returns null from every
 * method by default, which makes Spring's {@code TransactionTemplate.execute}
 * just call the callback and return its result. That's exactly what we
 * want for unit tests — no Hibernate, no DB, just verify the service
 * orchestrates the right calls in the right order.
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

	@Mock private TenantRepository tenantRepository;
	@Mock private UserRepository userRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private AuthService authService;
	@Mock private PlatformTransactionManager txManager;

	private TenantServiceImpl service;

	private static final String SLUG = "acme";
	private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@BeforeEach
	void setUp() {
		service = new TenantServiceImpl(
				tenantRepository,
				new TenantMapper(),
				userRepository,
				passwordEncoder,
				authService,
				txManager
		);
	}

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	// ===========================================================================
	// findBySlug
	// ===========================================================================

	@Nested
	@DisplayName("findBySlug — public lookup by slug")
	class FindBySlug {

		@Test
		@DisplayName("happy path — returns a TenantSummary projection")
		void returnsSummaryWhenSlugExists() {
			Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
			when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));

			var summary = service.findBySlug(SLUG);

			assertThat(summary.slug()).isEqualTo(SLUG);
			assertThat(summary.status()).isEqualTo(TenantStatus.ACTIVE);
		}

		@Test
		@DisplayName("trims whitespace before querying — '  acme  ' resolves to 'acme'")
		void trimsWhitespace() {
			Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
			when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));

			service.findBySlug("  acme  ");

			verify(tenantRepository).findBySlugIgnoreCase(SLUG);
		}

		@Test
		@DisplayName("blank slug → TenantNotFoundException, no DB call")
		void blankSlugThrowsImmediately() {
			assertThatThrownBy(() -> service.findBySlug("   "))
					.isInstanceOf(TenantNotFoundException.class);
			assertThatThrownBy(() -> service.findBySlug(null))
					.isInstanceOf(TenantNotFoundException.class);

			verify(tenantRepository, never()).findBySlugIgnoreCase(anyString());
		}

		@Test
		@DisplayName("not found → TenantNotFoundException")
		void unknownSlugThrows() {
			when(tenantRepository.findBySlugIgnoreCase("ghost")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.findBySlug("ghost"))
					.isInstanceOf(TenantNotFoundException.class);
		}
	}

	// ===========================================================================
	// findCurrent
	// ===========================================================================

	@Nested
	@DisplayName("findCurrent — authenticated /me")
	class FindCurrent {

		@Test
		@DisplayName("happy path — reads tenant id from TenantContext and returns full TenantResponse")
		void returnsFullProjectionFromTenantContext() {
			Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
			tenant.setPlan(TenantPlan.PRO);
			when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

			TenantResponse response = TenantContext.runAs(TENANT_ID, service::findCurrent);

			assertThat(response.slug()).isEqualTo(SLUG);
			assertThat(response.plan()).isEqualTo(TenantPlan.PRO);
		}

		@Test
		@DisplayName("missing TenantContext → UnauthorizedException(MISSING_TENANT_CONTEXT)")
		void missingContextThrowsUnauthorized() {
			TenantContext.clear();

			assertThatThrownBy(() -> service.findCurrent())
					.isInstanceOfSatisfying(UnauthorizedException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("MISSING_TENANT_CONTEXT"));

			verify(tenantRepository, never()).findById(any(UUID.class));
		}

		@Test
		@DisplayName("context bound to non-existent tenant → TenantNotFoundException")
		void unknownTenantIdThrows() {
			when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> TenantContext.runAs(TENANT_ID, service::findCurrent))
					.isInstanceOf(TenantNotFoundException.class);
		}
	}

	// ===========================================================================
	// updateCurrent
	// ===========================================================================

	@Nested
	@DisplayName("updateCurrent — partial PATCH")
	class UpdateCurrent {

		@Test
		@DisplayName("delegates to the mapper merge then saves the entity")
		void appliesPatchAndSaves() {
			Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
			tenant.setName("Old Name");
			when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
			when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

			UpdateTenantRequest patch = new UpdateTenantRequest(
					"New Name", null,
					new BrandingDto("#FF6900", null, null, null),
					null, null, null, null);

			TenantResponse response = TenantContext.runAs(TENANT_ID,
					() -> service.updateCurrent(patch));

			assertThat(response.name()).isEqualTo("New Name");
			assertThat(response.branding().primaryColor()).isEqualTo("#FF6900");
			verify(tenantRepository).save(tenant);
		}

		@Test
		@DisplayName("missing TenantContext on PATCH → UnauthorizedException, no save")
		void missingContextRejected() {
			TenantContext.clear();
			UpdateTenantRequest patch = new UpdateTenantRequest(
					"X", null, null, null, null, null, null);

			assertThatThrownBy(() -> service.updateCurrent(patch))
					.isInstanceOf(UnauthorizedException.class);

			verify(tenantRepository, never()).save(any(Tenant.class));
		}
	}

	// ===========================================================================
	// activateCurrent (BE-2.6)
	// ===========================================================================

	@Nested
	@DisplayName("activateCurrent — onboarding lifecycle PENDING → ACTIVE")
	class ActivateCurrent {

		@Test
		@DisplayName("PENDING → ACTIVE: sets status, persists, returns the saved snapshot")
		void promotesPendingToActive() {
			Tenant tenant = newTenant(SLUG, TenantStatus.PENDING);
			when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
			when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

			TenantResponse response = TenantContext.runAs(TENANT_ID, service::activateCurrent);

			assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
			assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);

			ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
			verify(tenantRepository).save(captor.capture());
			assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
		}

		@Test
		@DisplayName("already ACTIVE → idempotent no-op (returns current snapshot, no save)")
		void activeIsIdempotent() {
			Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
			when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

			TenantResponse response = TenantContext.runAs(TENANT_ID, service::activateCurrent);

			assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
			verify(tenantRepository, never()).save(any(Tenant.class));
		}

		@Test
		@DisplayName("SUSPENDED → ConflictException(TENANT_NOT_ACTIVATABLE), status stays SUSPENDED")
		void suspendedRefuses() {
			Tenant tenant = newTenant(SLUG, TenantStatus.SUSPENDED);
			when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

			assertThatThrownBy(() ->
					TenantContext.runAs(TENANT_ID, service::activateCurrent))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TENANT_NOT_ACTIVATABLE"));

			assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
			verify(tenantRepository, never()).save(any(Tenant.class));
		}

		@Test
		@DisplayName("INACTIVE → ConflictException(TENANT_NOT_ACTIVATABLE)")
		void inactiveRefuses() {
			Tenant tenant = newTenant(SLUG, TenantStatus.INACTIVE);
			when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

			assertThatThrownBy(() ->
					TenantContext.runAs(TENANT_ID, service::activateCurrent))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TENANT_NOT_ACTIVATABLE"));
		}

		@Test
		@DisplayName("missing TenantContext → UnauthorizedException, no DB hits")
		void missingContextRejected() {
			TenantContext.clear();

			assertThatThrownBy(() -> service.activateCurrent())
					.isInstanceOf(UnauthorizedException.class);

			verify(tenantRepository, never()).findById(any(UUID.class));
			verify(tenantRepository, never()).save(any(Tenant.class));
		}
	}

	// ===========================================================================
	// register (self-signup)
	// ===========================================================================

	@Nested
	@DisplayName("register — public self-signup")
	class Register {

		@Test
		@DisplayName("happy path — persists tenant + admin, issues session, returns AuthResponse")
		void registersTenantAndAdminAtomically() {
			RegisterTenantRequest request = sampleRequest("acme-co", "Founder", "founder@acme.test");

			when(tenantRepository.findBySlugIgnoreCase("acme-co")).thenReturn(Optional.empty());
			when(tenantRepository.saveAndFlush(any(Tenant.class)))
					.thenAnswer(inv -> {
						Tenant t = inv.getArgument(0);
						setIdViaReflection(t, TENANT_ID);
						t.setPublicUuid(UUID.randomUUID());
						return t;
					});
			when(passwordEncoder.encode("Sup3rSecret!")).thenReturn("hashed-pwd");
			User savedAdmin = new User();
			savedAdmin.setEmail("founder@acme.test");
			savedAdmin.setPublicUuid(UUID.randomUUID());
			when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedAdmin);

			AuthResponse expectedSession = new AuthResponse(
					"access.token", "refresh.token", "Bearer", 900L,
					new UserSummary(savedAdmin.getPublicUuid(), "Founder Doe",
							"founder@acme.test", null, UserStatus.ACTIVE));
			when(authService.issueSession(eq(savedAdmin), any(Tenant.class)))
					.thenReturn(expectedSession);

			AuthResponse session = service.register(request);

			assertThat(session).isSameAs(expectedSession);
			verify(tenantRepository).saveAndFlush(any(Tenant.class));
			verify(userRepository).saveAndFlush(any(User.class));
			verify(authService).issueSession(eq(savedAdmin), any(Tenant.class));

			ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
			verify(tenantRepository, times(1)).saveAndFlush(tenantCaptor.capture());
			Tenant persisted = tenantCaptor.getValue();
			assertThat(persisted.getStatus()).isEqualTo(TenantStatus.PENDING);
			assertThat(persisted.getPlan()).isEqualTo(TenantPlan.TRIAL);
			assertThat(persisted.getTrialEndsAt()).isNotNull();
		}

		@Test
		@DisplayName("normalizes slug + email (trim + lowercase) before persistence and lookup")
		void normalizesSlugAndEmail() {
			RegisterTenantRequest request = sampleRequest("  ACME-co  ", "Founder",
					"  Founder@ACME.test  ");

			when(tenantRepository.findBySlugIgnoreCase("acme-co")).thenReturn(Optional.empty());
			when(tenantRepository.saveAndFlush(any(Tenant.class)))
					.thenAnswer(inv -> {
						Tenant t = inv.getArgument(0);
						setIdViaReflection(t, TENANT_ID);
						t.setPublicUuid(UUID.randomUUID());
						return t;
					});
			when(passwordEncoder.encode(anyString())).thenReturn("hashed");
			when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
			when(authService.issueSession(any(User.class), any(Tenant.class)))
					.thenReturn(dummyAuthResponse());

			service.register(request);

			ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
			verify(userRepository).saveAndFlush(userCaptor.capture());
			assertThat(userCaptor.getValue().getEmail()).isEqualTo("founder@acme.test");

			ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
			verify(tenantRepository).saveAndFlush(tenantCaptor.capture());
			assertThat(tenantCaptor.getValue().getSlug()).isEqualTo("acme-co");
		}

		@Test
		@DisplayName("pre-check: slug already taken → ConflictException(TENANT_SLUG_TAKEN), no inserts")
		void rejectsTakenSlugUpFront() {
			RegisterTenantRequest request = sampleRequest("acme-co", "Founder", "founder@acme.test");
			Tenant existing = newTenant("acme-co", TenantStatus.ACTIVE);
			when(tenantRepository.findBySlugIgnoreCase("acme-co")).thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.register(request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TENANT_SLUG_TAKEN"));

			verify(tenantRepository, never()).saveAndFlush(any(Tenant.class));
			verify(userRepository, never()).saveAndFlush(any(User.class));
			verify(authService, never()).issueSession(any(User.class), any(Tenant.class));
		}

		@Test
		@DisplayName("race condition: pre-check passes but INSERT trips unique → translates to ConflictException(TENANT_SLUG_TAKEN)")
		void racingInsertSurfacesAsConflict() {
			RegisterTenantRequest request = sampleRequest("acme-co", "Founder", "founder@acme.test");

			when(tenantRepository.findBySlugIgnoreCase("acme-co")).thenReturn(Optional.empty());
			when(tenantRepository.saveAndFlush(any(Tenant.class)))
					.thenThrow(new DataIntegrityViolationException(
							"duplicate key violates uk_tenants_slug_active"));

			assertThatThrownBy(() -> service.register(request))
					.isInstanceOfSatisfying(ConflictException.class, ex -> {
						assertThat(ex.getCode()).isEqualTo("TENANT_SLUG_TAKEN");
						// The original DataIntegrityViolation must be preserved as `cause`
						// so logs can still trace back to the DB-level diagnostic.
						assertThat(ex.getCause()).isInstanceOf(DataIntegrityViolationException.class);
					});

			verify(userRepository, never()).saveAndFlush(any(User.class));
			verify(authService, never()).issueSession(any(User.class), any(Tenant.class));
		}
	}

	// ===========================================================================
	// Fixtures
	// ===========================================================================

	private static Tenant newTenant(String slug, TenantStatus status) {
		Tenant t = new Tenant();
		setIdViaReflection(t, TENANT_ID);
		t.setName("Demo Institution");
		t.setSlug(slug);
		t.setStatus(status);
		t.setPublicUuid(UUID.randomUUID());
		t.setPlan(TenantPlan.TRIAL);
		return t;
	}

	private static RegisterTenantRequest sampleRequest(String slug, String firstName, String email) {
		return new RegisterTenantRequest(
				"Acme Corp",
				slug,
				email,
				"Sup3rSecret!",
				firstName,
				"Doe"
		);
	}

	private static AuthResponse dummyAuthResponse() {
		return new AuthResponse(
				"access", "refresh", "Bearer", 900L,
				new UserSummary(UUID.randomUUID(), "Founder Doe", "founder@acme.test",
						null, UserStatus.ACTIVE));
	}

	private static void setIdViaReflection(Object entity, UUID id) {
		try {
			Class<?> clazz = entity.getClass();
			while (clazz != null) {
				try {
					Field f = clazz.getDeclaredField("id");
					f.setAccessible(true);
					f.set(entity, id);
					return;
				}
				catch (NoSuchFieldException ignored) {
					clazz = clazz.getSuperclass();
				}
			}
			throw new IllegalStateException("No 'id' field found in " + entity.getClass());
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
