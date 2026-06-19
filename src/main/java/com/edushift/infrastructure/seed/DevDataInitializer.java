package com.edushift.infrastructure.seed;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.List;
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
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;
	private final TransactionTemplate txTemplate;

	private final String adminEmail;
	private final String adminPassword;
	private final String adminFirstName;
	private final String adminLastName;
	private final String tenantSlug;
	private final String seedPassword;

	private static final String SEED_HASH_SENTINEL = "SEED_RESET_REQUIRED_v1";
	private static final String FIND_TENANTS_SQL = """
			SELECT id
			FROM edushift.tenants
			WHERE deleted = false
			""";

	public DevDataInitializer(
			JdbcTemplate jdbcTemplate,
			UserRepository userRepository,
			TenantRepository tenantRepository,
			PasswordEncoder passwordEncoder,
			PlatformTransactionManager txManager,
			@Value("${dev.seed.admin.email:admin@demo.edushift.pe}") String adminEmail,
			@Value("${dev.seed.admin.password:Edushift123!}") String adminPassword,
			@Value("${dev.seed.admin.first-name:Admin}") String adminFirstName,
			@Value("${dev.seed.admin.last-name:Demo}") String adminLastName,
			@Value("${dev.seed.admin.tenant-slug:demo}") String tenantSlug,
			@Value("${dev.seed.password:EduShift2026!}") String seedPassword) {
		this.jdbcTemplate = jdbcTemplate;
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.passwordEncoder = passwordEncoder;
		this.txTemplate = new TransactionTemplate(txManager);
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
		this.adminFirstName = adminFirstName;
		this.adminLastName = adminLastName;
		this.tenantSlug = tenantSlug;
		this.seedPassword = seedPassword;
	}

	@Override
	public void run(String... args) {
		// 1) Reset V38-seeded user password hashes (sentinel → real BCrypt of seedPassword)
		resetSeededUserPasswords();

		// 2) Tenant lookup runs WITHOUT tenant context — tenants is the global catalog
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

	/**
	 * V38 seeds user rows with a sentinel password hash that REJECTS login
	 * (cannot be a real BCrypt). On dev startup we walk every tenant and
	 * overwrite those sentinel hashes with a real BCrypt of
	 * {@code dev.seed.password} (default {@code EduShift2026!}) so the
	 * seeded admin / teachers / parents can log in.
	 *
	 * <p>Idempotent: rows whose password_hash no longer starts with
	 * {@link #SEED_HASH_SENTINEL} are skipped, so re-runs are no-ops once
	 * the sentinel has been replaced.</p>
	 *
	 * <p>Multi-tenancy note: the same dance as {@link #run} — enter
	 * {@code runAs} per tenant, then open a new transaction inside. We use
	 * raw SQL to find the sentinel rows because the {@code @TenantId}
	 * filter is the last thing to apply, and we want to update them all
	 * without writing a JPA entity for the sentinel state.</p>
	 */
	private void resetSeededUserPasswords() {
		List<UUID> tenantIds = jdbcTemplate.query(FIND_TENANTS_SQL,
				(rs, rowNum) -> (UUID) rs.getObject(1));

		String findSentinelsSql = """
				SELECT id, email
				FROM edushift.users
				WHERE deleted = false
				  AND password_hash LIKE ?
				""";
		String updateSql = """
				UPDATE edushift.users
				SET    password_hash = ?, updated_at = NOW()
				WHERE  id = ?
				""";
		String sentinelPrefix = SEED_HASH_SENTINEL + "%";

		int totalReset = 0;
		for (UUID tid : tenantIds) {
			final int[] count = { 0 };
			TenantContext.runAs(tid, () -> {
				txTemplate.executeWithoutResult(status -> {
					List<Object[]> rows = jdbcTemplate.query(
							findSentinelsSql,
							(rs, rowNum) -> new Object[] {
									(UUID) rs.getObject(1),
									rs.getString(2)
							},
							sentinelPrefix);

					String realHash = passwordEncoder.encode(seedPassword);
					for (Object[] row : rows) {
						UUID userId = (UUID) row[0];
						String email = (String) row[1];
						jdbcTemplate.update(updateSql, realHash, userId);
						count[0]++;
						log.info("[dev-seed] reset password for seeded user '{}' "
								+ "(tenant={}, password='{}')",
								email, tid, seedPassword);
					}
				});
				return null;
			});
			totalReset += count[0];
		}

		if (totalReset == 0) {
			log.debug("[dev-seed] no seeded users with sentinel hash found — nothing to reset");
		} else {
			log.info("[dev-seed] reset {} seeded user password(s) to real BCrypt of '{}'",
					totalReset, seedPassword);
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
		// Sprint 2 (BE-2.4): the seed admin needs TENANT_ADMIN so the dev
		// environment can exercise role-gated endpoints (e.g.
		// PATCH /tenants/me). The Flyway V8 backfill covers tenants whose
		// seed admin already exists; this line covers fresh boots.
		user.addRole(com.edushift.modules.auth.entity.UserRole.TENANT_ADMIN);
		// publicUuid is generated in @PrePersist; tenant_id is auto-populated
		// by Hibernate's @TenantId discriminator from TenantContext.
		return user;
	}

}
