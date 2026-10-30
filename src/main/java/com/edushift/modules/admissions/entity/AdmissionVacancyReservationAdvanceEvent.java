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
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admission_vacancy_reservation_advance_events", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AdmissionVacancyReservationAdvanceEvent extends TenantAwareEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "advance_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admission_vacancy_reservation_advance_events_advance_tenant"))
	private AdmissionVacancyReservationAdvance advance;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 24)
	private AdmissionReservationAdvanceEventAction action;

	@Column(name = "amount_cents", nullable = false)
	private long amountCents;

	@Column(name = "invoice_public_uuid")
	private UUID invoicePublicUuid;

	@Column(name = "note", length = 500)
	private String note;

	@Column(name = "actor_user_id")
	private UUID actorUserId;
}
