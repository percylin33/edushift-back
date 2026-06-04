package com.edushift.infrastructure.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers correlation and access-log filters at the very start of the
 * Servlet filter chain (well before Spring Security) so every subsequent log
 * line carries a correlation id and is captured in the access log.
 */
@Configuration
public class WebLoggingConfiguration {

	@Bean
	FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
		FilterRegistrationBean<CorrelationIdFilter> reg =
				new FilterRegistrationBean<>(new CorrelationIdFilter());
		reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
		reg.addUrlPatterns("/*");
		reg.setName("correlationIdFilter");
		return reg;
	}

	@Bean
	FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
		FilterRegistrationBean<RequestLoggingFilter> reg =
				new FilterRegistrationBean<>(new RequestLoggingFilter());
		reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		reg.addUrlPatterns("/*");
		reg.setName("requestLoggingFilter");
		return reg;
	}

}
