package com.edushift.modules.notifications.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Admin SDK settings used by notifications (FCM) and optional auth verification.
 * Bound to {@code app.integrations.firebase.*}.
 * <p>
 * Provide credentials via {@code credentialsJson} (raw JSON in env, preferred for
 * container deploys) <strong>or</strong> {@code credentialsPath} (file path mounted as secret).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.integrations.firebase")
public class FirebaseProperties {

	private boolean enabled = false;

	/** Firebase project identifier. */
	private String projectId;

	/** Service-account credentials as raw JSON (FIREBASE_CREDENTIALS_JSON). */
	private String credentialsJson;

	/** Service-account credentials file path (FIREBASE_CREDENTIALS_PATH). */
	private String credentialsPath;

	/** Web API key for client SDKs (FIREBASE_WEB_API_KEY). */
	private String webApiKey;

	/** Optional database URL (Realtime Database). */
	private String databaseUrl;

	/** Optional storage bucket. */
	private String storageBucket;

}
