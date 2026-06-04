package com.edushift.infrastructure.multitenancy;

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
 * Wires the tenant context, Hibernate multi-tenancy and the MVC interceptor.
 * <p>
 * Filter runs near the end of the filter chain (after Spring Security has
 * had a chance to populate the principal); the interceptor then validates
 * the binding against the authenticated principal.
 */
@Configuration
@RequiredArgsConstructor
public class MultiTenancyConfiguration implements WebMvcConfigurer {

	private final TenantInterceptor tenantInterceptor;

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
	}

}
