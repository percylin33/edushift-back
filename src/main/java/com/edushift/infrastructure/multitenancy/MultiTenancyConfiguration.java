package com.edushift.infrastructure.multitenancy;

import com.edushift.infrastructure.ratelimit.RateLimitInterceptor;
import com.edushift.shared.multitenancy.TenantResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the tenant context, Hibernate multi-tenancy and the MVC interceptors.
 *
 * <p>Filter runs near the end of the filter chain (after Spring Security has
 * had a chance to populate the principal); the interceptor then validates
 * the binding against the authenticated principal.</p>
 *
 * <p>Also registers the {@link RateLimitInterceptor} for the
 * {@link com.edushift.infrastructure.ratelimit.RateLimitProperties.Rule}
 * paths declared in {@code edushift.ratelimit.rules}. The interceptor
 * matches each request against the rules list and short-circuits to 429
 * on breach. Adding/removing a rule does not require touching this class —
 * just edit {@code application*.properties}.</p>
 */
@Configuration
@RequiredArgsConstructor
public class MultiTenancyConfiguration implements WebMvcConfigurer {

	private final TenantInterceptor tenantInterceptor;
	private final RateLimitInterceptor rateLimitInterceptor;

	@Bean
	CurrentTenantIdentifierResolver<UUID> currentTenantIdentifierResolver() {
		return new TenantIdResolver();
	}

	@Bean
	HibernatePropertiesCustomizer multiTenancyHibernateCustomizer(
			CurrentTenantIdentifierResolver<UUID> resolver) {
		return props -> props.put("hibernate.tenant_identifier_resolver", resolver);
	}

	@Bean
	FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantResolver tenantResolver) {
		FilterRegistrationBean<TenantFilter> registration =
				new FilterRegistrationBean<>(new TenantFilter(tenantResolver));
		registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
		registration.addUrlPatterns("/*");
		registration.setName("tenantFilter");
		return registration;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tenantInterceptor)
				.addPathPatterns("/**")
				.excludePathPatterns("/actuator/**");

		// Rate-limit coverage is path-driven by
		// edushift.ratelimit.rules. Registering on /** here is a
		// catch-all; RateLimitInterceptor itself does the per-rule
		// matching and skips paths without a configured rule.
		registry.addInterceptor(rateLimitInterceptor)
				.addPathPatterns("/v1/**");
	}
}
