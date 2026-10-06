package com.edushift.modules.files.config;

import com.edushift.modules.files.storage.StoredObject;
import java.util.Arrays;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective storage environment segment for object keys
 * ({@code envs/{env}/...}).
 *
 * <p>Priority: {@code app.storage.env} / {@code STORAGE_ENV} when set;
 * otherwise derived from Spring active profiles ({@code prod} →
 * {@code prod}, {@code staging} → {@code staging}, else {@code dev}).
 */
@Component
@RequiredArgsConstructor
public class StorageEnvResolver {

	private final StorageProperties storageProperties;
	private final Environment environment;

	public String resolveEnv() {
		String configured = storageProperties.getEnv();
		if (configured != null && !configured.isBlank()) {
			return StoredObject.sanitizeEnv(configured);
		}
		String[] profiles = environment.getActiveProfiles();
		boolean prod = Arrays.stream(profiles)
				.map(p -> p.toLowerCase(Locale.ROOT))
				.anyMatch(p -> p.equals("prod") || p.equals("production"));
		if (prod) {
			return "prod";
		}
		boolean staging = Arrays.stream(profiles)
				.map(p -> p.toLowerCase(Locale.ROOT))
				.anyMatch(p -> p.equals("staging") || p.equals("uat"));
		if (staging) {
			return "staging";
		}
		return "dev";
	}
}
