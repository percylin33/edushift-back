package com.edushift.modules.notifications.fcm;

import com.edushift.modules.notifications.config.FirebaseProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bootstraps {@link FirebaseApp} for FCM (Sprint cierre-C / B8).
 *
 * <p>Mirrors the storage pattern in
 * {@code com.edushift.modules.files.storage.FirebaseStorageService}: read
 * credentials from {@code app.integrations.firebase.credentials-json}
 * (preferred for container deploys) or
 * {@code app.integrations.firebase.credentials-path} (mounted secret).</p>
 *
 * <p>Only present when {@code app.integrations.firebase.enabled=true}.
 * When absent, {@link FcmSender} gracefully no-ops so the app keeps
 * starting and notifications still get persisted + emitted over the
 * in-app channel.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.integrations.firebase.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FcmInitializer {

	private final FirebaseProperties props;
	private FirebaseApp app;

	@PostConstruct
	void init() throws IOException {
		GoogleCredentials credentials = loadCredentials();
		if (credentials == null) {
			// Do not crash the whole app on PaaS when FCM is toggled on without secrets.
			// FcmSender already no-ops when FirebaseApp was never initialized.
			log.warn("[fcm-init] app.integrations.firebase.enabled=true but no credentials-json "
					+ "or credentials-path configured — FCM disabled for this process. "
					+ "Set FIREBASE_ENABLED=false or provide FIREBASE_CREDENTIALS_JSON / path.");
			return;
		}
		FirebaseOptions.Builder builder = FirebaseOptions.builder()
				.setCredentials(credentials)
				.setProjectId(props.getProjectId());
		if (props.getDatabaseUrl() != null && !props.getDatabaseUrl().isBlank()) {
			builder.setDatabaseUrl(props.getDatabaseUrl());
		}
		FirebaseOptions options = builder.build();
		this.app = FirebaseApp.initializeApp(options, "edushift-notifications");
		log.info("[fcm-init] FirebaseApp initialized project={}", props.getProjectId());
	}

	@PreDestroy
	void close() {
		if (app != null) {
			app.delete();
			log.info("[fcm-init] FirebaseApp deleted");
		}
	}

	public FirebaseApp app() { return app; }

	public FirebaseMessaging messaging() {
		if (app == null) {
			throw new IllegalStateException("FirebaseApp not initialized; check app.integrations.firebase.* properties");
		}
		return FirebaseMessaging.getInstance(app);
	}

	private GoogleCredentials loadCredentials() throws IOException {
		if (props.getCredentialsJson() != null && !props.getCredentialsJson().isBlank()) {
			byte[] raw = props.getCredentialsJson().getBytes(StandardCharsets.UTF_8);
			try (InputStream in = new ByteArrayInputStream(raw)) {
				log.info("[fcm-init] loading credentials from inline credentialsJson ({} bytes)", raw.length);
				return GoogleCredentials.fromStream(in);
			}
		}
		if (props.getCredentialsPath() != null && !props.getCredentialsPath().isBlank()) {
			Path path = Path.of(props.getCredentialsPath());
			try (InputStream in = Files.newInputStream(path)) {
				log.info("[fcm-init] loading credentials from file path {}", path);
				return GoogleCredentials.fromStream(in);
			}
		}
		return null;
	}
}