package com.edushift.modules.claims.entity;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "claims_book_entries", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class ClaimsBookEntry extends AuditableEntity {

	@Column(name = "tenant_id")
	private UUID tenantId;

	@Column(name = "claim_code", nullable = false, unique = true, length = 30)
	private String claimCode;

	@Column(name = "consumer_name", nullable = false, length = 200)
	private String consumerName;

	@Column(name = "consumer_doc_type", nullable = false, length = 20)
	private String consumerDocType;

	@Column(name = "consumer_doc_number", nullable = false, length = 20)
	private String consumerDocNumber;

	@Column(name = "consumer_email", nullable = false, length = 254)
	private String consumerEmail;

	@Column(name = "consumer_phone", nullable = false, length = 32)
	private String consumerPhone;

	@Column(name = "consumer_address", nullable = false, length = 300)
	private String consumerAddress;

	@Column(name = "is_minor", nullable = false)
	private Boolean isMinor = false;

	@Column(name = "parent_guardian_name", length = 200)
	private String parentGuardianName;

	@Column(name = "contracted_service", nullable = false, length = 200)
	private String contractedService;

	@Column(name = "claim_amount", precision = 12, scale = 2)
	private BigDecimal claimAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "claim_type", nullable = false, length = 20)
	private ClaimType claimType;

	@Column(name = "detail_description", nullable = false, columnDefinition = "TEXT")
	private String detailDescription;

	@Column(name = "consumer_request", nullable = false, columnDefinition = "TEXT")
	private String consumerRequest;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ClaimStatus status = ClaimStatus.PENDING;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(name = "response_text", columnDefinition = "TEXT")
	private String responseText;

	@Column(name = "responded_at")
	private Instant respondedAt;

	public enum ClaimType {
		RECLAMO, QUEJA
	}

	public enum ClaimStatus {
		PENDING, IN_PROCESS, ATTENDED
	}

	@PrePersist
	private void onPrePersist() {
		if (isMinor == null) {
			isMinor = false;
		}
		if (status == null) {
			status = ClaimStatus.PENDING;
		}
	}
}
