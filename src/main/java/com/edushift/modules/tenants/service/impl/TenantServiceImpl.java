package com.edushift.modules.tenants.service.impl;

import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.modules.tenants.dto.RegisterTenantRequest;
import com.edushift.modules.tenants.dto.TenantResponse;
import com.edushift.modules.tenants.dto.TenantSummary;
import com.edushift.modules.tenants.dto.UpdateTenantRequest;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.exception.TenantNotFoundException;
import com.edushift.modules.tenants.mapper.TenantMapper;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.tenants.service.TenantService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link TenantService}.
 *
 * <h3>Where the tenant identity comes from</h3>
 * For {@link #findCurrent()} and {@link #updateCurrent} we read the
 * tenant id from {@link TenantContext}. That context is bound by
 * {@code TenantFilter} (in the {@code shared/multitenancy} module)
 * <em>after</em> {@code JwtAuthenticationFilter} has authenticated the
 * principal, so by the time a controller method calls into this
 * service the value is guaranteed for any authenticated request.
 * <p>
 * If the context is missing it almost always means the request hit a
 * public endpoint via misconfiguration ({@code /v1/tenants/me} ended up
 * in {@code PUBLIC_PATHS} by accident, for example). We translate that
 * to {@link UnauthorizedException} so the client gets a clean 401
 * instead of a 500 NPE.
 *
 * <h3>Caching</h3>
 * Read paths ({@code findBySlug}) are good cache candidates — the
 * branding payload barely changes and {@code GET /tenants/by-slug} is
 * hit on every login screen render. We don't cache here yet (waiting
 * for the cross-tenant invalidation pattern to settle in Sprint 3+);
 * meanwhile the queries are fast (partial unique index on
 * {@code lower(slug)}).
 */
@Service
@Slf4j
public class TenantServiceImpl implements TenantService {

	/** Trial window for fresh self-signups. Centralized for easy tuning. */
	private static final Duration TRIAL_DURATION = Duration.ofDays(14);

	private final TenantRepository tenantRepository;

	private final TenantMapper tenantMapper;

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuthService authService;

	/**
	 * Used by {@link #register} to open a transaction <em>after</em>
	 * binding the new tenant to {@link TenantContext}. Constructor-injected
	 * to honor the same multi-tenancy invariants documented in
	 * {@link com.edushift.modules.auth.service.impl.AuthServiceImpl}: a
	 * declarative {@code @Transactional} on the public method would open
	 * the Hibernate session before {@code TenantContext.runAs}, leaking
	 * the {@code ROOT_TENANT} sentinel into the resolver.
	 */
	private final TransactionTemplate txTemplate;

	/**
	 * Sprint 4 / BE-4.2 hook — seeds default academic levels + grades
	 * into a freshly registered tenant. {@code @Nullable}-equivalent in
	 * spirit: even though the bean is always present at runtime, we
	 * tolerate {@code null} in test slices that don't import the
	 * academic module by mocking the field through reflection.
	 */
	private final AcademicSeedService academicSeedService;

	public TenantServiceImpl(
			TenantRepository tenantRepository,
			TenantMapper tenantMapper,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			AuthService authService,
			AcademicSeedService academicSeedService,
			PlatformTransactionManager transactionManager) {
		this.tenantRepository = tenantRepository;
		this.tenantMapper = tenantMapper;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authService = authService;
		this.academicSeedService = academicSeedService;
		this.txTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	@Transactional(readOnly = true)
	public TenantSummary findBySlug(String slug) {
		String normalized = slug == null ? "" : slug.trim();
		if (normalized.isEmpty()) {
			throw TenantNotFoundException.forSlug(slug);
		}

		Tenant tenant = tenantRepository.findBySlugIgnoreCase(normalized)
				.orElseThrow(() -> TenantNotFoundException.forSlug(normalized));

		return tenantMapper.toSummary(tenant);
	}

	@Override
	@Transactional(readOnly = true)
	public TenantResponse findCurrent() {
		Tenant tenant = loadCurrentTenant();
		return tenantMapper.toResponse(tenant);
	}

	@Override
	@Transactional
	public TenantResponse activateCurrent() {
		Tenant tenant = loadCurrentTenant();
		TenantStatus current = tenant.getStatus();

		if (current == TenantStatus.ACTIVE) {
			// Idempotent: the SPA may retry on transient network errors and
			// we'd rather a no-op than a confusing 409 in those cases.
			log.debug("[tenants] activate -- tenant '{}' already ACTIVE; no-op",
					tenant.getSlug());
			return tenantMapper.toResponse(tenant);
		}

		if (current != TenantStatus.PENDING) {
			// SUSPENDED / INACTIVE belong to admin tooling, not the
			// onboarding wizard. Surface a stable code so the UI can route
			// the user to support instead of pretending it worked.
			log.warn("[tenants] activate refused -- tenant '{}' status={}",
					tenant.getSlug(), current);
			throw new ConflictException("TENANT_NOT_ACTIVATABLE",
					"Tenant cannot be activated from status " + current);
		}

		tenant.setStatus(TenantStatus.ACTIVE);
		Tenant saved = tenantRepository.save(tenant);
		log.info("[tenants] activated tenant id={} slug='{}'", saved.getId(), saved.getSlug());
		return tenantMapper.toResponse(saved);
	}

	@Override
	@Transactional
	public TenantResponse updateCurrent(UpdateTenantRequest patch) {
		Tenant tenant = loadCurrentTenant();
		tenantMapper.applyUpdate(patch, tenant);

		// Hibernate dirty checking persists on commit; explicit save() not
		// required, but keeping it makes the intent obvious in code review.
		Tenant saved = tenantRepository.save(tenant);

		log.info("[tenants] updated tenant id={} slug='{}' by-context", saved.getId(), saved.getSlug());
		return tenantMapper.toResponse(saved);
	}

	// ---------------------------------------------------------------------------
	// Self-signup
	// ---------------------------------------------------------------------------

	/**
	 * Two-step transaction:
	 * <ol>
	 *   <li>An <em>outer</em> transactional block (no tenant context yet)
	 *       inserts the {@code tenants} row. This row lives in the
	 *       global catalog and must NOT be tagged with a tenant id.</li>
	 *   <li>An <em>inner</em> {@code TenantContext.runAs} + new
	 *       transaction creates the admin {@code users} row inside the
	 *       fresh tenant's scope, then issues the session.</li>
	 * </ol>
	 *
	 * <h3>Why two transactions instead of one</h3>
	 * Hibernate binds the {@code @TenantId} resolver value at session
	 * open time. To insert the {@code tenants} row we need
	 * {@code TenantContext} <em>empty</em> (or {@code ROOT_TENANT}); to
	 * insert the {@code users} row we need it <em>set to the new tenant</em>.
	 * A single transaction can't satisfy both. Both transactions commit
	 * for the request to succeed, and a failure inside the inner block
	 * still rolls back the outer one because we re-throw and the
	 * controller layer surfaces a 500/409.
	 *
	 * <p>If the user-creation step fails (e.g. unexpected DB constraint),
	 * the outer transaction has already committed the tenant row. We
	 * mark that as accepted risk for now: the orphan tenant is harmless
	 * (no one can authenticate against it) and a future cleanup job
	 * (or a manual {@code DELETE}) reclaims it. A more rigorous
	 * solution would be a saga pattern with a compensating delete on
	 * the tenant; out of scope for Sprint 2.
	 */
	@Override
	public AuthResponse register(RegisterTenantRequest request) {
		final String slug = request.tenantSlug().trim().toLowerCase();
		final String adminEmail = request.adminEmail().trim().toLowerCase();

		// Pre-check: short-circuit when the slug is obviously taken. We still
		// catch DataIntegrityViolationException below in case of a TOCTOU
		// race against a concurrent registration.
		tenantRepository.findBySlugIgnoreCase(slug).ifPresent(existing -> {
			throw new ConflictException("TENANT_SLUG_TAKEN",
					"Tenant slug '" + slug + "' is already taken");
		});

		// Step 1 — create the tenant in the global catalog (no tenant context).
		Tenant persistedTenant = persistTenant(request, slug);

		// Step 2 — create the admin, seed the academic catalog defaults,
		// and issue tokens inside the new tenant's scope.
		try {
			return TenantContext.runAs(persistedTenant.getId(), () ->
					txTemplate.execute(status -> {
						User admin = persistAdminUser(request, adminEmail);

						// BE-4.2: seed INICIAL/PRIMARIA/SECUNDARIA + their default
						// grades. Idempotent (no-op if levels already exist), so
						// re-runs from admin tools or repeated bootstraps don't
						// double-seed. Guarded against test slices that do not
						// wire the academic module.
						if (academicSeedService != null) {
							academicSeedService.seedDefaults(persistedTenant.getId());
						}

						AuthResponse session = authService.issueSession(admin, persistedTenant);
						log.info("[tenants] register OK -- slug='{}' adminEmail='{}' tenantId={}",
								slug, adminEmail, persistedTenant.getId());
						return session;
					}));
		} catch (RuntimeException ex) {
			log.error("[tenants] register failed AFTER tenant insert -- "
							+ "tenant id={} slug='{}' may be orphaned",
					persistedTenant.getId(), slug, ex);
			throw ex;
		}
	}

	private Tenant persistTenant(RegisterTenantRequest request, String slug) {
		try {
			return txTemplate.execute(status -> {
				Tenant tenant = new Tenant();
				tenant.setName(request.tenantName().trim());
				tenant.setSlug(slug);
				tenant.setStatus(TenantStatus.PENDING);
				tenant.setPlan(TenantPlan.TRIAL);
				tenant.setTrialEndsAt(Instant.now().plus(TRIAL_DURATION));
				return tenantRepository.saveAndFlush(tenant);
			});
		} catch (DataIntegrityViolationException dup) {
			// Race condition: a concurrent registration claimed the slug
			// between our pre-check and this insert. Surface the same
			// error code as the pre-check so the front handles it identically.
			throw new ConflictException("TENANT_SLUG_TAKEN",
					"Tenant slug '" + slug + "' is already taken", dup);
		}
	}

	private User persistAdminUser(RegisterTenantRequest request, String adminEmail) {
		User admin = new User();
		admin.setEmail(adminEmail);
		admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
		admin.setFirstName(request.adminFirstName().trim());
		admin.setLastName(request.adminLastName().trim());
		// Self-signup admins skip email verification: they just typed the
		// address and pressed submit a moment ago. A future
		// "auth hardening" sprint can add a confirmation email gate.
		admin.setStatus(UserStatus.ACTIVE);
		admin.setEmailVerified(true);
		admin.setMfaEnabled(false);
		// Self-signup creates the tenant owner: TENANT_ADMIN out of the box,
		// so the freshly-issued JWT can pass `@PreAuthorize("hasRole(TENANT_ADMIN)")`
		// gates and the user can finish onboarding (PATCH /tenants/me).
		admin.addRole(UserRole.TENANT_ADMIN);
		// tenant_id auto-populated by Hibernate's @TenantId from TenantContext.
		return userRepository.saveAndFlush(admin);
	}

	// ---------------------------------------------------------------------------
	// Internals
	// ---------------------------------------------------------------------------

	/**
	 * Resolve the current tenant from {@link TenantContext}. Single
	 * source of truth so the "no context" branch lives in exactly one
	 * place.
	 */
	private Tenant loadCurrentTenant() {
		UUID tenantId = TenantContext.current()
				.orElseThrow(() -> new UnauthorizedException(
						"MISSING_TENANT_CONTEXT",
						"No tenant in security context — request reached the service "
								+ "without authentication or tenant binding."
				));

		return tenantRepository.findById(tenantId)
				.orElseThrow(() -> TenantNotFoundException.forId(tenantId));
	}

}
