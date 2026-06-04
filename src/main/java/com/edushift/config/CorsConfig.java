package com.edushift.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-Origin Resource Sharing configuration.
 *
 * <h3>Why this exists as a separate class</h3>
 * Spring Security's {@code http.cors(Customizer.withDefaults())} (or any
 * non-empty {@code .cors(...)} call) enables the {@code CorsFilter} but
 * looks up the {@link CorsConfigurationSource} bean by name from the
 * application context. Without this bean defined, the filter would
 * default to a no-op and every preflight {@code OPTIONS} would fall
 * through to {@code SecurityFilterChain.authorize}, which (correctly,
 * from its perspective) rejects unauthenticated cross-origin requests
 * with 403 — exactly the symptom we hit on the first runtime smoke.
 *
 * <h3>Where the config comes from</h3>
 * Origins, methods, headers, credentials and max-age are bound from
 * {@code app.cors.*} via {@link AppProperties.Cors}. Defaults are tuned
 * for a SaaS API: all REST verbs, all request headers, credentials on
 * (because the access token rides in the {@code Authorization} header
 * and the browser only sends it across origins when {@code Allow-Credentials}
 * is true). Origins are an explicit allow-list — never use {@code "*"}
 * with credentials enabled (the browser silently strips the cookie /
 * Authorization header in that case).
 *
 * <h3>Why path = "/**"</h3>
 * The {@link UrlBasedCorsConfigurationSource} matches against the
 * servlet path (after the {@code /api} context-path is stripped), so
 * registering at {@code /**} covers the entire API surface uniformly.
 * If we ever need to differentiate (e.g. tighter origins for {@code /v1/auth/**}
 * than for {@code /v1/public/**}), we add more {@code registerCorsConfiguration}
 * calls here — Spring picks the most specific match per request.
 */
@Configuration
public class CorsConfig {

	private final AppProperties appProperties;

	public CorsConfig(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		AppProperties.Cors corsProps = appProperties.getCors();

		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(corsProps.getAllowedOrigins());
		config.setAllowedMethods(corsProps.getAllowedMethods());
		config.setAllowedHeaders(corsProps.getAllowedHeaders());
		config.setAllowCredentials(corsProps.isAllowCredentials());
		config.setMaxAge(corsProps.getMaxAge());

		// Surface the auth + tenant headers to the browser so SDKs can read
		// them programmatically. Add more here as new headers are introduced.
		config.setExposedHeaders(List.of("Authorization", "X-Tenant-Slug", "Location"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

}
