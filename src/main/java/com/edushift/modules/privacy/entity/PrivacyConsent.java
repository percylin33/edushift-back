package com.edushift.modules.privacy.entity;

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
@Table(name = "privacy_consents", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class PrivacyConsent extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@Column(name = "guardian_user_id", nullable = false)
	private UUID guardianUserId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "policy_version", nullable = false, length = 20)
	private String policyVersion = "v1.0";

	@Column(name = "purpose_code", nullable = false, length = 80)
	private String purposeCode;

	@Column(name = "notice_snapshot", nullable = false, columnDefinition = "text")
	private String noticeSnapshot;

	@Column(name = "consent_granted", nullable = false)
	private Boolean consentGranted = true;

	@Column(name = "granted_at", nullable = false)
	private Instant grantedAt;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(name = "user_agent")
	private String userAgent;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (grantedAt == null) {
			grantedAt = Instant.now();
		}
		if (consentGranted == null) {
			consentGranted = true;
		}
		if (policyVersion == null) {
			policyVersion = "v1.0";
		}
	}
}
