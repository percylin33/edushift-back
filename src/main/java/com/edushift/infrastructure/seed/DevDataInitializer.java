package com.edushift.infrastructure.seed;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds a deterministic admin account for the {@code demo} tenant on
 * application startup, but only when the {@code dev} profile is active.
 *
 * <h3>Why a {@link CommandLineRunner} and not a Flyway seed migration?</h3>
 * <ul>
 *   <li>The password is hashed with the production {@link PasswordEncoder}
 *       (BCrypt, configurable cost) — guaranteeing 1:1 fidelity with the real
 *       login flow. A hand-crafted hash in SQL is brittle: a typo silently
 *       breaks login.</li>
 *   <li>The runner is naturally idempotent: it checks for existence before
 *       inserting and re-runs are no-ops.</li>
 *   <li>The credentials can be overridden via environment variables without
 *       editing source.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code DEV_ADMIN_EMAIL}    (default: {@code admin@demo.edushift.pe})</li>
 *   <li>{@code DEV_ADMIN_PASSWORD} (default: {@code Edushift123!})</li>
 *   <li>{@code DEV_ADMIN_FIRST_NAME} (default: {@code Admin})</li>
 *   <li>{@code DEV_ADMIN_LAST_NAME}  (default: {@code Demo})</li>
 *   <li>{@code DEV_ADMIN_TENANT_SLUG} (default: {@code demo})</li>
 * </ul>
 *
 * <p><strong>Safety:</strong> the bean is annotated with {@code @Profile("dev")}.
 * Production deployments use {@code prod} and never instantiate this class.
 */
@Slf4j
@Component
@Profile("dev")
public class DevDataInitializer implements CommandLineRunner {

	private static final String FIND_TENANT_SQL = """
			SELECT id
			FROM edushift.tenants
			WHERE lower(slug) = lower(?) AND deleted = false
			""";

	private final JdbcTemplate jdbcTemplate;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TransactionTemplate txTemplate;

	private final String adminEmail;
	private final String adminPassword;
	private final String adminFirstName;
	private final String adminLastName;
	private final String tenantSlug;

	public DevDataInitializer(
			JdbcTemplate jdbcTemplate,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			PlatformTransactionManager txManager,
			@Value("${dev.seed.admin.email:admin@demo.edushift.pe}") String adminEmail,
			@Value("${dev.seed.admin.password:Edushift123!}") String adminPassword,
			@Value("${dev.seed.admin.first-name:Admin}") String adminFirstName,
			@Value("${dev.seed.admin.last-name:Demo}") String adminLastName,
			@Value("${dev.seed.admin.tenant-slug:demo}") String tenantSlug) {
		this.jdbcTemplate = jdbcTemplate;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.txTemplate = new TransactionTemplate(txManager);
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
		this.adminFirstName = adminFirstName;
		this.adminLastName = adminLastName;
		this.tenantSlug = tenantSlug;
	}

	@Override
	public void run(String... args) {
		// Tenant lookup runs WITHOUT tenant context — tenants is the global catalog
		// and JdbcTemplate manages its own connection / transaction.
		UUID tenantId = findDemoTenantId();
		if (tenantId == null) {
			log.warn("[dev-seed] tenant '{}' not found — skipping admin seed. "
					+ "Did Flyway V5 run?", tenantSlug);
			return;
		}

		// Same multi-tenancy ordering bug as AuthServiceImpl had: if we annotated
		// run() with @Transactional, Hibernate would open the session BEFORE we
		// got a chance to call TenantContext.runAs, so the @TenantId resolver
		// would return ROOT_TENANT and the admin row would be persisted with
		// tenant_id = 00000000-0000-0000-0000-000000000000 — silently breaking
		// every subsequent multi-tenant query against that user. Order is:
		//
		//   1. runAs(...) sets the thread-local context
		//   2. txTemplate.execute(...) opens a NEW session, resolver fires NOW
		//      and sees the real tenant id
		//   3. existsByEmail / save run inside the bound session
		TenantContext.runAs(tenantId, () -> {
			txTemplate.executeWithoutResult(status -> {
				if (userRepository.existsByEmail(adminEmail.toLowerCase())) {
					log.info("[dev-seed] admin '{}' already exists for tenant '{}' — no-op",
							adminEmail, tenantSlug);
					return;
				}

				User admin = buildAdmin(adminEmail, adminPassword, adminFirstName, adminLastName);
				userRepository.save(admin);

				log.info("[dev-seed] created dev admin: email='{}', tenant='{}', publicUuid='{}'. "
						+ "Default password: '{}' (override with DEV_SEED_ADMIN_PASSWORD).",
						admin.getEmail(), tenantSlug, admin.getPublicUuid(), adminPassword);
			});
			return null;
		});
	}

	private UUID findDemoTenantId() {
		try {
			return jdbcTemplate.queryForObject(FIND_TENANT_SQL, UUID.class, tenantSlug);
		}
		catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	private User buildAdmin(String email, String rawPassword,
	                        String firstName, String lastName) {
		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setFirstName(firstName);
		user.setLastName(lastName);
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerified(true);
		user.setMfaEnabled(false);
		// publicUuid is generated in @PrePersist; tenant_id is auto-populated
		// by Hibernate's @TenantId discriminator from TenantContext.
		return user;
	}

}
