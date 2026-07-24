package com.edushift.modules.notifications.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FCM device token (Sprint cierre-C / B8).
 *
 * <p>One row per (FCM token, tenant). The token is globally unique;
 * when a user reinstalls the app and rotates the token, the BE
 * upserts on {@code token} and reassigns the {@code userPublicUuid}.</p>
 *
 * <p>Soft-delete via {@link #active} + {@link #unregisteredAt}: the
 * row stays visible for audit, but the FCM sender never picks it up
 * (the dispatch query is filtered on {@code active = true}).</p>
 */
@Entity
@Table(
		name = "user_device_tokens",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_user_device_tokens_token", columnNames = "token")
		},
		indexes = {
				@Index(name = "idx_user_device_tokens_user_active",
						columnList = "tenant_id, user_public_uuid, last_seen_at DESC")
		}
)
@Getter
@Setter
@NoArgsConstructor
public class UserDeviceToken extends TenantAwareEntity {

	public enum Platform { ANDROID, IOS, WEB }

	@Column(name = "user_public_uuid", nullable = false)
	private UUID userPublicUuid;

	@Column(name = "token", nullable = false, length = 512, unique = true)
	private String token;

	@Enumerated(EnumType.STRING)
	@Column(name = "platform", nullable = false, length = 10)
	private Platform platform = Platform.ANDROID;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt = Instant.now();

	@Column(name = "unregistered_at")
	private Instant unregisteredAt;
}