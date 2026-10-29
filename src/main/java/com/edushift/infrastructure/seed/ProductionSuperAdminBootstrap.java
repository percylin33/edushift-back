package com.edushift.infrastructure.seed;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit bootstrap for the first platform administrator; disabled by default. */
@Slf4j
@Component
@Profile({"dev", "local", "prod"})
public class ProductionSuperAdminBootstrap implements CommandLineRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PlatformTransactionManager txManager;
	private final boolean enabled;
	private final String email;
	private final String temporaryPassword;

	public ProductionSuperAdminBootstrap(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			PlatformTransactionManager txManager,
			@Value("${edushift.bootstrap.super-admin.enabled:false}") boolean enabled,
			@Value("${edushift.bootstrap.super-admin.email:}") String email,
			@Value("${edushift.bootstrap.super-admin.temporary-password:}") String temporaryPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.txManager = txManager;
		this.enabled = enabled;
		this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		this.temporaryPassword = temporaryPassword;
	}

	@Override
	public void run(String... args) {
		if (!enabled) return;
		if (email.isBlank() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
			throw new IllegalStateException("Bootstrap SUPER_ADMIN is enabled but its email is missing or invalid");
		}
		if (temporaryPassword == null || temporaryPassword.length() < 12) {
			throw new IllegalStateException("Bootstrap SUPER_ADMIN is enabled but its temporary password is missing or too short");
		}

		TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () -> {
			new TransactionTemplate(txManager).executeWithoutResult(status -> {
				if (userRepository.existsByEmail(email)) {
					log.info("[prod-bootstrap] configured SUPER_ADMIN already exists; no changes made");
					return;
				}
				User user = new User();
				user.setEmail(email);
				user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
				user.setFirstName("Super");
				user.setLastName("Admin");
				user.setStatus(UserStatus.ACTIVE);
				user.setEmailVerified(true);
				user.setMfaEnabled(false);
				user.setMustChangePassword(true);
				user.setRoleSet(Set.of(UserRole.SUPER_ADMIN));
				userRepository.saveAndFlush(user);
				log.warn("[prod-bootstrap] initial SUPER_ADMIN created; temporary credential must be changed at first login");
			});
			return null;
		});
	}
}
