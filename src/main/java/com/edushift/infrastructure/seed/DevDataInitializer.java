package com.edushift.infrastructure.seed;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.security.SecureRandom;
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

@Slf4j
@Component
@Profile({"dev", "local"})
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
	private final SecureRandom secureRandom = new SecureRandom();

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

	/**
	 * Sentinel password hash stamped onto a SUPER_ADMIN that was created
	 * with auto-generated credentials. The hash is detected at login time
	 * by {@code AdminAuthService} which rejects it with
	 * {@code PASSWORD_RESET_REQUIRED} so the operator must run the
	 * break-glass recovery flow before any other action.
	 *
	 * <p>This is intentionally NOT a bcrypt hash — the constant-time
	 * decoder in {@link #isSeedPasswordResetSentinel(String)} matches by
	 * prefix so any attempt to treat it as a real hash fails fast.</p>
	 */
	private static final String SUPER_ADMIN_RESET_SENTINEL_HASH = "SUPER_ADMIN_RESET_REQUIRED_v1";

	// --- SUPER_ADMIN seed configuration (Sprint 15 / F-01 / H-01) ----
	/**
	 * Set {@code EDUSHIFT_SEED_SUPER_ADMIN=true} (or
	 * {@code dev.seed.super-admin.enabled=true}) to seed a SUPER_ADMIN
	 * on startup. Defaults to {@code false} — the profile gate
	 * ({@code dev,local}) is no longer sufficient on its own to create a
	 * platform-tier account.
	 */
	private final boolean superAdminSeedEnabled;
	private final String superAdminEmail;
	private final String superAdminPasswordRaw;
	private final boolean superAdminPasswordAuto;

	// =====================================================================
	// Sprint 15 / BE-15.1: SUPER_ADMIN seed constants
	// =====================================================================
	private static final UUID SUPER_ADMIN_SENTINEL_TENANT =
			UUID.fromString("00000000-0000-0000-0000-000000000001");

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
			@Value("${dev.seed.password:EduShift2026!}") String seedPassword,
			@Value("${dev.seed.super-admin.enabled:${edushift.seed.super-admin.enabled:false}}")
					boolean superAdminSeedEnabled,
			@Value("${dev.seed.super-admin.email:${edushift.seed.super-admin.email:super@edushift.pe}}")
					String superAdminEmail,
			@Value("${dev.seed.super-admin.password:${edushift.seed.super-admin.password:}}")
					String superAdminPasswordRaw) {
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
		this.superAdminSeedEnabled = superAdminSeedEnabled;
		this.superAdminEmail = superAdminEmail;
		this.superAdminPasswordRaw = superAdminPasswordRaw;
		// If a password was supplied, use it verbatim; otherwise generate a
		// strong random one at runtime and print it once. The random path is
		// the recommended default — it never puts a publicly-known password
		// into source control.
		this.superAdminPasswordAuto = (superAdminPasswordRaw == null || superAdminPasswordRaw.isBlank());
	}

	@Override
	public void run(String... args) {
		resetSeededUserPasswords();

		UUID tenantId = findDemoTenantId();
		if (tenantId == null) {
			log.warn("[dev-seed] tenant '{}' not found — skipping admin seed. "
					+ "Did Flyway V5 run?", tenantSlug);
		}
		else {
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

		// ===================================================================
		// Sprint 15 / BE-15.1 / F-01 / H-01: SUPER_ADMIN seed is opt-in.
		// The @Profile gate alone is not sufficient — operators running a
		// dev pod by accident must not auto-create a SUPER_ADMIN with a
		// publicly-known password. We require an explicit env var.
		// ===================================================================
		if (!superAdminSeedEnabled) {
			log.info("[dev-seed] SUPER_ADMIN seed skipped (set EDUSHIFT_SEED_SUPER_ADMIN=true "
					+ "or dev.seed.super-admin.enabled=true to enable).");
		}
		else {
			seedSuperAdmin();
		}
	}

	/**
	 * Seed the SUPER_ADMIN account for the {@code edushift-system} sentinel
	 * tenant. Behavior:
	 * <ul>
	 *   <li>If {@code dev.seed.super-admin.password} is set, it is hashed
	 *       and persisted directly (logged at INFO; never written to
	 *       source).</li>
	 *   <li>If no password is supplied, a strong random 24-char password
	 *       is generated at startup and logged once at INFO. The DB row is
	 *       stamped with a sentinel hash so {@code AdminAuthService} rejects
	 *       the seed credential and forces the operator through the
	 *       break-glass recovery flow before any privileged action.</li>
	 *   <li>MFA is intentionally NOT enabled: the seeded operator must
	 *       complete TOTP enrolment on first login (H-02 enforces this).</li>
	 * </ul>
	 */
	private void seedSuperAdmin() {
		TenantContext.runAs(SUPER_ADMIN_SENTINEL_TENANT, () -> {
			txTemplate.executeWithoutResult(status -> {
				if (userRepository.existsByEmail(superAdminEmail.toLowerCase())) {
					log.info("[dev-seed] SUPER_ADMIN '{}' already exists — no-op",
							superAdminEmail);
					return;
				}

				String dbPasswordHash;
				String passwordForLog;
				if (superAdminPasswordAuto) {
					dbPasswordHash = SUPER_ADMIN_RESET_SENTINEL_HASH;
					passwordForLog = generateRandomPassword();
					log.warn("====================================================================");
					log.warn("[dev-seed] NEW SUPER_ADMIN SEEDED — break-glass credentials "
							+ "(shown once, NOT recoverable):");
					log.warn("[dev-seed]   email:    {}", superAdminEmail);
					log.warn("[dev-seed]   password: {}", passwordForLog);
					log.warn("[dev-seed] On first login the seed credential will be rejected "
							+ "with PASSWORD_RESET_REQUIRED. Use POST /admin/recover to bootstrap.");
					log.warn("====================================================================");
				}
				else {
					dbPasswordHash = passwordEncoder.encode(superAdminPasswordRaw);
					passwordForLog = "[from env var dev.seed.super-admin.password]";
					log.warn("[dev-seed] SUPER_ADMIN seeded with password from env var. "
							+ "First login will require MFA enrolment (H-02).");
				}

				User superAdmin = new User();
				superAdmin.setEmail(superAdminEmail);
				superAdmin.setPasswordHash(dbPasswordHash);
				superAdmin.setFirstName("Super");
				superAdmin.setLastName("Admin");
				superAdmin.setStatus(UserStatus.ACTIVE);
				superAdmin.setEmailVerified(true);
				superAdmin.setMfaEnabled(false);
				superAdmin.addRole(UserRole.SUPER_ADMIN);
				userRepository.save(superAdmin);

				log.info("[dev-seed] created SUPER_ADMIN: email='{}', publicUuid='{}'. "
						+ "Credentials: {}", superAdmin.getEmail(),
						superAdmin.getPublicUuid(), passwordForLog);
			});
			return null;
		});
	}

	/**
	 * Returns {@code true} when the password hash stored on the user row
	 * is one of the sentinel non-bcrypt placeholders. Public so that
	 * {@code AdminAuthService} can use the same predicate at login time.
	 */
	public static boolean isSeedPasswordResetSentinel(String hash) {
		return hash != null
				&& (hash.startsWith("SEED_RESET_REQUIRED_v1")
						|| hash.startsWith("SUPER_ADMIN_RESET_REQUIRED_v1"));
	}

	private String generateRandomPassword() {
		String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%^&*";
		StringBuilder sb = new StringBuilder(24);
		for (int i = 0; i < 24; i++) {
			sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
		}
		return sb.toString();
	}

	private UUID findDemoTenantId() {
		try {
			return jdbcTemplate.queryForObject(FIND_TENANT_SQL, UUID.class, tenantSlug);
		}
		catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

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
		}
		else {
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
		user.addRole(com.edushift.modules.auth.entity.UserRole.TENANT_ADMIN);
		return user;
	}

}
