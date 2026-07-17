package com.edushift.infrastructure.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link RateLimitProperties} so {@code @Value} / {@code @Autowired}
 * consumers can inject them. The {@link RateLimitInterceptor} takes the
 * properties directly via constructor injection.
 *
 * <p>Default rules are declared in {@code application*.properties} under
 * the {@code edushift.ratelimit.rules} key. See
 * {@code docs/qa/10-rate-limit-spec-y-status.md} for the canonical defaults.</p>
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
	// Marker class — Spring picks up the @ConfigurationProperties bean
	// from the static initializer below.
}
