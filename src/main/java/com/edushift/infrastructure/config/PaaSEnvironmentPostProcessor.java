package com.edushift.infrastructure.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * PaaS (Render / Railway / Heroku) env normalisation.
 *
 * <ul>
 *   <li>Converts {@code DATABASE_URL=postgresql://…} into JDBC datasource props
 *       when {@code SPRING_DATASOURCE_URL} is absent.</li>
 *   <li>Fails fast if a Postgres URL was mistakenly assigned to Redis
 *       ({@code SPRING_DATA_REDIS_URL} / {@code REDIS_URL}).</li>
 * </ul>
 */
public class PaaSEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final String SOURCE_NAME = "paassEnvironmentPostProcessor";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		rejectPostgresUrlAsRedis(environment);

		Map<String, Object> additions = new HashMap<>();
		maybeDisableRedis(environment, additions);
		maybeMapDatabaseUrl(environment, additions);
		maybeMapRedisUrl(environment, additions);

		if (!additions.isEmpty()) {
			environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, additions));
		}
	}

	private static void maybeDisableRedis(ConfigurableEnvironment environment, Map<String, Object> additions) {
		String enabled = environment.getProperty("edushift.redis.enabled",
				environment.getProperty("EDUSHIFT_REDIS_ENABLED", "true"));
		if (!"false".equalsIgnoreCase(enabled)) {
			return;
		}

		additions.put("edushift.redis.enabled", "false");
		String extra = String.join(",",
				"org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
				"org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
				"org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration");
		String existing = environment.getProperty("spring.autoconfigure.exclude", "");
		additions.put("spring.autoconfigure.exclude",
				existing.isBlank() ? extra : existing + "," + extra);
	}

	private static void rejectPostgresUrlAsRedis(ConfigurableEnvironment environment) {
		for (String key : new String[] {
				"SPRING_DATA_REDIS_URL",
				"spring.data.redis.url",
				"REDIS_URL"
		}) {
			String value = environment.getProperty(key);
			if (value != null && looksLikePostgresUrl(value)) {
				throw new IllegalStateException(
						"Env var '" + key + "' contains a PostgreSQL URL, but Redis expects "
								+ "redis:// or rediss://. On Render: set SPRING_DATA_REDIS_URL "
								+ "(or REDIS_URL) from the Redis instance, and keep DATABASE_URL "
								+ "only for Postgres (or map it to SPRING_DATASOURCE_URL=jdbc:${DATABASE_URL}). "
								+ "Current scheme='" + schemeOf(value) + "'.");
			}
		}
	}

	private static void maybeMapDatabaseUrl(ConfigurableEnvironment environment, Map<String, Object> additions) {
		if (hasText(environment.getProperty("SPRING_DATASOURCE_URL"))
				|| hasText(environment.getProperty("spring.datasource.url"))) {
			return;
		}

		String databaseUrl = environment.getProperty("DATABASE_URL");
		if (!hasText(databaseUrl) || !looksLikePostgresUrl(databaseUrl)) {
			return;
		}

		try {
			ParsedDb parsed = parsePostgresUrl(databaseUrl);
			additions.put("spring.datasource.url", parsed.jdbcUrl());
			if (!hasText(environment.getProperty("SPRING_DATASOURCE_USERNAME"))
					&& !hasText(environment.getProperty("spring.datasource.username"))
					&& !hasText(environment.getProperty("DB_USER"))) {
				additions.put("spring.datasource.username", parsed.username());
			}
			if (!hasText(environment.getProperty("SPRING_DATASOURCE_PASSWORD"))
					&& !hasText(environment.getProperty("spring.datasource.password"))
					&& !hasText(environment.getProperty("DB_PASSWORD"))) {
				additions.put("spring.datasource.password", parsed.password());
			}
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException("Invalid DATABASE_URL: " + ex.getMessage(), ex);
		}
	}

	private static void maybeMapRedisUrl(ConfigurableEnvironment environment, Map<String, Object> additions) {
		if (hasText(environment.getProperty("SPRING_DATA_REDIS_URL"))
				|| hasText(environment.getProperty("spring.data.redis.url"))) {
			return;
		}

		String redisUrl = environment.getProperty("REDIS_URL");
		if (!hasText(redisUrl)) {
			return;
		}
		if (looksLikePostgresUrl(redisUrl)) {
			return; // already rejected above when key is REDIS_URL
		}
		if (redisUrl.startsWith("redis://") || redisUrl.startsWith("rediss://")) {
			additions.put("spring.data.redis.url", redisUrl);
			if (redisUrl.startsWith("rediss://")) {
				additions.put("spring.data.redis.ssl.enabled", "true");
			}
		}
	}

	private static ParsedDb parsePostgresUrl(String raw) throws URISyntaxException {
		String normalized = raw.trim();
		if (normalized.startsWith("postgres://")) {
			normalized = "postgresql://" + normalized.substring("postgres://".length());
		}

		URI uri = URI.create(normalized);
		String userInfo = uri.getUserInfo();
		String username = "";
		String password = "";
		if (userInfo != null) {
			int colon = userInfo.indexOf(':');
			if (colon >= 0) {
				username = userInfo.substring(0, colon);
				password = userInfo.substring(colon + 1);
			}
			else {
				username = userInfo;
			}
		}

		String host = uri.getHost();
		int port = uri.getPort() > 0 ? uri.getPort() : 5432;
		String path = uri.getPath() != null ? uri.getPath() : "";
		String database = path.startsWith("/") ? path.substring(1) : path;
		if (database.contains("?")) {
			database = database.substring(0, database.indexOf('?'));
		}

		String query = uri.getQuery();
		StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
				.append(host).append(':').append(port).append('/').append(database);
		if (query != null && !query.isBlank()) {
			jdbc.append('?').append(query);
			if (!query.contains("sslmode=")) {
				jdbc.append("&sslmode=require");
			}
		}
		else {
			jdbc.append("?sslmode=require");
		}

		return new ParsedDb(jdbc.toString(), username, password);
	}

	private static boolean looksLikePostgresUrl(String value) {
		String v = value.trim().toLowerCase();
		return v.startsWith("postgresql://") || v.startsWith("postgres://");
	}

	private static String schemeOf(String value) {
		int idx = value.indexOf("://");
		return idx > 0 ? value.substring(0, idx) : "(none)";
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

	private record ParsedDb(String jdbcUrl, String username, String password) {
	}
}
