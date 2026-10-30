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
@Table(name = "admission_vacancy_reservation_events", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AdmissionVacancyReservationEvent extends TenantAwareEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reservation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admission_vacancy_reservation_events_reservation_tenant"))
	private AdmissionVacancyReservation reservation;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 20)
	private AdmissionReservationStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 20)
	private AdmissionReservationStatus toStatus;

	@Column(name = "note", length = 1000)
	private String note;

	@Column(name = "actor_user_id")
	private UUID actorUserId;
}
