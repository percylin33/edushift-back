package com.edushift.modules.notifications.fcm;

import com.edushift.modules.notifications.entity.UserDeviceToken;
import com.edushift.modules.notifications.repository.UserDeviceTokenRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends push notifications via FCM (Sprint cierre-C / B8).
 *
 * <p>Pulls the active tokens for the recipient via
 * {@link UserDeviceTokenRepository}, fans out via
 * {@code sendEachForMulticast}, and flips the tokens that the FCM API
 * returned as {@code UNREGISTERED} / {@code INVALID_ARGUMENT} to
 * {@code active=false} so we never dispatch to them again.</p>
 *
 * <p>Best-effort: any {@link FirebaseMessagingException} is logged at
 * WARN and swallowed so the notification pipeline (DB + in-app channel)
 * never depends on FCM being healthy.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.integrations.firebase.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FcmSender {

	private final UserDeviceTokenRepository deviceTokenRepository;
	@Autowired(required = false)
	private FcmInitializer fcmInitializer;

	@Value("${app.integrations.firebase.fcm-async:true}")
	private boolean async;

	@PostConstruct
	void probe() {
		if (fcmInitializer == null) {
			log.warn("[fcm-sender] FirebaseInitializer not present; FcmSender is a no-op. "
					+ "Check that the firebase-messaging dependency is on the classpath.");
		}
		else {
			log.info("[fcm-sender] FCM ready, async={}", async);
		}
	}

	public void send(UUID recipientUserPublicUuid, String title, String body, Map<String, String> data) {
		if (fcmInitializer == null) return;

		List<UserDeviceToken> tokens = deviceTokenRepository
				.findByTenantIdAndUserPublicUuidAndActiveTrueOrderByLastSeenAtDesc(
						currentTenant(), recipientUserPublicUuid);
		if (tokens.isEmpty()) {
			log.debug("[fcm-sender] no active tokens for user {}", recipientUserPublicUuid);
			return;
		}

		if (async) {
			dispatchAsync(recipientUserPublicUuid, tokens, title, body, data);
		}
		else {
			dispatchNow(recipientUserPublicUuid, tokens, title, body, data);
		}
	}

	@Async("fcmExecutor")
	void dispatchAsync(UUID recipient, List<UserDeviceToken> tokens,
	                   String title, String body, Map<String, String> data) {
		dispatchNow(recipient, tokens, title, body, data);
	}

	private void dispatchNow(UUID recipient, List<UserDeviceToken> tokens,
	                         String title, String body, Map<String, String> data) {
		List<String> registrationTokens = tokens.stream()
				.map(UserDeviceToken::getToken)
				.toList();

		Map<String, String> payload = new HashMap<>();
		if (data != null) payload.putAll(data);
		payload.putIfAbsent("recipientUserPublicUuid", recipient.toString());
		payload.putIfAbsent("deliveredAt", Instant.now().toString());

		Notification notification = Notification.builder()
				.setTitle(title == null ? "" : title)
				.setBody(body == null ? "" : body)
				.build();
		MulticastMessage message = MulticastMessage.builder()
				.addAllTokens(registrationTokens)
				.setNotification(notification)
				.putAllData(payload)
				.build();

		try {
			BatchResponse batch = fcmInitializer.messaging().sendEachForMulticast(message);
			List<SendResponse> responses = batch.getResponses();
			int success = batch.getSuccessCount();
			int failed = batch.getFailureCount();
			int deactivated = 0;
			for (int i = 0; i < responses.size(); i++) {
				SendResponse r = responses.get(i);
				if (r.isSuccessful()) continue;
				String errorCode = "unknown";
				if (r.getException() != null && r.getException().getMessagingErrorCode() != null) {
					errorCode = r.getException().getMessagingErrorCode().name();
				}
				else if (r.getException() != null && r.getException().getErrorCode() != null) {
					errorCode = r.getException().getErrorCode().name();
				}
				if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
					deactivate(registrationTokens.get(i));
					deactivated++;
				}
				else {
					log.warn("[fcm-sender] token {} failed: {}", i, errorCode);
				}
			}
			log.info("[fcm-sender] user={} success={} failed={} deactivated={}",
					recipient, success, failed, deactivated);
		}
		catch (FirebaseMessagingException ex) {
			log.warn("[fcm-sender] multicast failed for user {}: {}", recipient, ex.getMessage());
		}
	}

	private void deactivate(String token) {
		deviceTokenRepository.findByToken(token).ifPresent(t -> {
			t.setActive(false);
			t.setUnregisteredAt(Instant.now());
			deviceTokenRepository.save(t);
		});
	}

	private UUID currentTenant() {
		return com.edushift.shared.multitenancy.TenantContext.currentRequired();
	}
}