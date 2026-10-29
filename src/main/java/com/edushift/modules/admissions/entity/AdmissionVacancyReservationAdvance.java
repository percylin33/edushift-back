package com.edushift.modules.admissions.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(name = "admission_vacancy_reservation_advances", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE edushift.admission_vacancy_reservation_advances SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
public class AdmissionVacancyReservationAdvance extends TenantAwareEntity {
	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reservation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admission_vacancy_reservation_advances_reservation_tenant"))
	private AdmissionVacancyReservation reservation;

	@Column(name = "amount_cents", nullable = false)
	private long amountCents;

	@Column(name = "applied_cents", nullable = false)
	private long appliedCents;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "PEN";

	@Enumerated(EnumType.STRING)
	@Column(name = "method", nullable = false, length = 20)
	private AdmissionReservationAdvanceMethod method;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 24)
	private AdmissionReservationAdvanceStatus status = AdmissionReservationAdvanceStatus.AVAILABLE;

	@Column(name = "reference", length = 120)
	private String reference;

	@Column(name = "notes", length = 500)
	private String notes;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
		if (receivedAt == null) receivedAt = Instant.now();
	}
}
