package com.edushift.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger UI bootstrap for springdoc.
 * <p>
 * Defines a global {@code bearerAuth} security scheme that endpoints can opt
 * into via {@code @SecurityRequirement(name = "bearerAuth")} so the Swagger UI
 * exposes the "Authorize" button and threads the {@code Authorization: Bearer}
 * header through subsequent "Try it out" requests.
 *
 * <p>The spec lives at {@code /api/v3/api-docs} and the UI at
 * {@code /api/swagger-ui/index.html} once the {@code /api}
 * {@code server.servlet.context-path} prefix is applied.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_AUTH_SCHEME = "bearerAuth";

	@Value("${spring.application.name:edushift-back}")
	private String applicationName;

	@Bean
	OpenAPI edushiftOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("EduShift API")
						.description("""
								Multi-tenant educational SaaS platform — REST API.

								Authenticate via `POST /v1/auth/login` with your tenant slug \
								in the `X-Tenant-Slug` header to obtain an access + refresh \
								token pair. Use the "Authorize" button above to set the bearer \
								token for protected endpoints.
								""")
						.version("v1")
						.contact(new Contact().name("EduShift Engineering").email("dev@edushift.pe"))
						.license(new License().name("Proprietary").url("https://edushift.pe")))
				.servers(List.of(
						new Server().url("/api").description("Same-origin (default; respects context-path)"),
						new Server().url("http://localhost:8080/api").description("Local development")))
				.components(new Components()
						.addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
								.name(BEARER_AUTH_SCHEME)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Paste the access token returned by `/v1/auth/login`. "
										+ "The 'Bearer ' prefix is added automatically.")));
	}

}
