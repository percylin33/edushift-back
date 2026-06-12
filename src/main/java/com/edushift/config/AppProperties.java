package com.edushift.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-wide settings: frontend URL, CORS, public base URL.
 * Bound to {@code app.*}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

	/** Public base URL of this backend (e.g. https://api.edushift.com). */
	private String baseUrl;

	private final Frontend frontend = new Frontend();

	private final Cors cors = new Cors();

	@Getter
	@Setter
	public static class Frontend {

		/** Primary frontend URL used for emails, links and OAuth redirects. */
		private String url;

	}

	@Getter
	@Setter
	public static class Cors {

		private List<String> allowedOrigins = List.of();

		/**
		 * Wildcard-friendly origin patterns (e.g. {@code https://*.devtunnels.ms},
		 * {@code https://*.ngrok-free.app}) used together with
		 * {@code setAllowedOriginPatterns}. Required when {@code allowCredentials=true}
		 * because Spring forbids the literal {@code "*"} in {@code allowedOrigins}
		 * in that case. Empty by default — populated only in dev profiles.
		 */
		private List<String> allowedOriginPatterns = List.of();

		private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

		private List<String> allowedHeaders = List.of("*");

		private boolean allowCredentials = true;

		private long maxAge = 3600L;

	}

}
