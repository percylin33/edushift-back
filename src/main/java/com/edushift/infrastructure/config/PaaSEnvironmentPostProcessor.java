package com.edushift.infrastructure.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
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
		ensureSslForManagedPostgres(environment, additions);
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
			rejectBareRenderInternalHost(parsed.jdbcUrl());
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

	/**
	 * Render Internal hostnames look like {@code dpg-xxxxx-a} (no dots). They only
	 * resolve on Render's private network when the Web Service and DB share a region
	 * AND the DB is linked. If DNS fails, fail fast with actionable instructions
	 * instead of a buried Flyway {@code UnknownHostException}.
	 */
	private static void rejectBareRenderInternalHost(String jdbcOrDbUrl) {
		String host = extractHost(jdbcOrDbUrl);
		if (host == null || !host.matches("(?i)dpg-[a-z0-9]+-a")) {
			return;
		}
		try {
			InetAddress.getByName(host);
		}
		catch (UnknownHostException ex) {
			throw new IllegalStateException(
					"Render Internal hostname '" + host + "' does not resolve (UnknownHostException). "
							+ "Fix ONE of these:\n"
							+ "  1) Prefer External URL — Render → Postgres → Connect → External Database URL, then set:\n"
							+ "       SPRING_DATASOURCE_URL=jdbc:postgresql://" + host
							+ ".<region>-postgres.render.com:5432/<dbname>?sslmode=require\n"
							+ "       SPRING_DATASOURCE_USERNAME=...\n"
							+ "       SPRING_DATASOURCE_PASSWORD=...\n"
							+ "       (and remove/override DATABASE_URL if it still points at '" + host + "')\n"
							+ "  2) Or keep Internal — put Web Service and Postgres in the SAME region and "
							+ "Link the database to this service in Render.",
					ex);
		}
	}

	private static String extractHost(String url) {
		if (url == null) {
			return null;
		}
		try {
			String normalized = url.trim();
			if (normalized.startsWith("jdbc:")) {
				normalized = normalized.substring("jdbc:".length());
			}
			if (normalized.startsWith("postgres://")) {
				normalized = "postgresql://" + normalized.substring("postgres://".length());
			}
			if (!normalized.contains("://")) {
				normalized = "postgresql://" + normalized;
			}
			return URI.create(normalized).getHost();
		}
		catch (Exception ignored) {
			return null;
		}
	}

	/**
	 * Render / Railway / Neon / etc. reject non-TLS clients with
	 * {@code FATAL: SSL/TLS required}. If the JDBC URL (or {@code DB_SSL_MODE})
	 * still says {@code disable}/{@code prefer} against a managed host, force
	 * {@code sslmode=require}.
	 */
	private static void ensureSslForManagedPostgres(
			ConfigurableEnvironment environment, Map<String, Object> additions) {
		String jdbcUrl = firstText(
				additions.get("spring.datasource.url"),
				environment.getProperty("SPRING_DATASOURCE_URL"),
				environment.getProperty("spring.datasource.url"));
		if (hasText(jdbcUrl)) {
			rejectBareRenderInternalHost(jdbcUrl);
		}
		if (!hasText(jdbcUrl)) {
			String databaseUrl = environment.getProperty("DATABASE_URL");
			if (hasText(databaseUrl) && looksLikePostgresUrl(databaseUrl) && isManagedPostgresHost(databaseUrl)) {
				additions.put("DB_SSL_MODE", "require");
			}
			return;
		}

		if (!isManagedPostgresHost(jdbcUrl)) {
			return;
		}

		String forced = forceSslModeRequire(jdbcUrl);
		if (!forced.equals(jdbcUrl)) {
			additions.put("spring.datasource.url", forced);
			additions.put("SPRING_DATASOURCE_URL", forced);
		}
		additions.put("DB_SSL_MODE", "require");
	}

	private static String forceSslModeRequire(String jdbcUrl) {
		if (jdbcUrl.contains("sslmode=require")) {
			return jdbcUrl;
		}
		String without = jdbcUrl
				.replaceAll("(?i)([?&])sslmode=[^&]*", "$1")
				.replaceAll("\\?&", "?")
				.replaceAll("\\?$", "");
		return without.contains("?")
				? without + "&sslmode=require"
				: without + "?sslmode=require";
	}

	private static boolean isManagedPostgresHost(String urlOrHost) {
		String v = urlOrHost.toLowerCase();
		return v.contains("render.com")
				|| v.contains("rlwy.net")
				|| v.contains("neon.tech")
				|| v.contains("supabase.co")
				|| v.contains("amazonaws.com")
				|| v.matches("(?s).*\\bdpg-[a-z0-9]+-a\\b.*");
	}

	private static String firstText(Object... values) {
		for (Object value : values) {
			if (value != null && hasText(String.valueOf(value))) {
				return String.valueOf(value);
			}
		}
		return null;
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
