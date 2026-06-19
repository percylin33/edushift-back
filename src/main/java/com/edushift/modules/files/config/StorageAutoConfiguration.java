package com.edushift.modules.files.config;

import com.edushift.modules.files.error.FilesErrorCodes;
import com.edushift.modules.files.storage.LocalFsStorageService;
import com.edushift.modules.files.storage.StorageProvider;
import com.edushift.modules.files.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link StorageService} implementation selected by
 * {@code app.storage.provider} (Sprint 7a / BE-7a.0, ADR-7A.1).
 *
 * <h3>Selection</h3>
 * Each implementation is gated by
 * {@code @ConditionalOnProperty(name="app.storage.provider",
 * havingValue="…")}. At most one of them is materialised as a bean,
 * so the standard Spring injection (by type) into
 * {@code FileObjectService} resolves to it directly — no custom
 * factory needed.
 *
 * <h3>Multipart size cap</h3>
 * Spring's standard multipart resolver is configured via
 * {@code spring.servlet.multipart.*}. The
 * {@code app.storage.max-file-size-bytes} value is mirrored to those
 * properties in {@code application.properties} so the framework
 * rejects oversized uploads before they reach the controller (this
 * is the standard Spring Boot pattern; we do not need a custom
 * {@code MultipartResolver} bean).
 *
 * <h3>Fail-fast</h3>
 * A typo in {@code app.storage.provider=firebae} (for example) means
 * no {@code @ConditionalOnProperty} matches and no {@link StorageService}
 * bean is registered, which causes Spring to throw
 * {@code NoSuchBeanDefinitionException} when wiring
 * {@code FileObjectService}. The application refuses to boot rather
 * than silently falling back to LOCAL_FS.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

	/**
	 * Resolves a {@link StorageProvider} from a string, throwing
	 * {@link IllegalArgumentException} with the
	 * {@link FilesErrorCodes#UNKNOWN_STORAGE_PROVIDER} code on a typo.
	 * Exposed as a static helper so callers (CLI tools, custom
	 * {@code @Value} parsers) can reuse the same validation.
	 */
	public static StorageProvider requireValid(String value) {
		for (StorageProvider p : StorageProvider.values()) {
			if (p.name().equalsIgnoreCase(value)) {
				return p;
			}
		}
		throw new IllegalArgumentException(
				"Unknown storage provider: " + value
						+ " (expected one of: " + java.util.Arrays.toString(StorageProvider.values())
						+ ", code=" + FilesErrorCodes.UNKNOWN_STORAGE_PROVIDER + ")");
	}

	// Keep a no-arg reference to LocalFsStorageService so the
	// dependency graph is visible at startup even when the
	// conditional is false in this profile.
	@SuppressWarnings("unused")
	private static final Class<?> KEEP_REFERENCE_FOR_DOC = LocalFsStorageService.class;
}
