package com.edushift.modules.payments.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_access_overrides", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAccessOverride extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@Column(name = "user_public_uuid", nullable = false)
	private UUID userPublicUuid;

	@Column(name = "student_public_uuid")
	private UUID studentPublicUuid;

	@Column(name = "reason", length = 500)
	private String reason;

	@Column(name = "until_at", nullable = false)
	private Instant untilAt;

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
	}
}
