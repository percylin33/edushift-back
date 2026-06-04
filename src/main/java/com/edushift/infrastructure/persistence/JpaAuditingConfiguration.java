package com.edushift.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA Auditing so {@code @CreatedDate}, {@code @LastModifiedDate},
 * {@code @CreatedBy} and {@code @LastModifiedBy} populate automatically from the
 * current authenticated principal exposed via {@link SecurityAuditorAware}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
public class JpaAuditingConfiguration {
}
