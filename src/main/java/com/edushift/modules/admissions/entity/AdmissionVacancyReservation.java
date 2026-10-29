package com.edushift.modules.admissions.entity;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
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
@Table(name = "admission_vacancy_reservations", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE edushift.admission_vacancy_reservations SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
public class AdmissionVacancyReservation extends TenantAwareEntity {
	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admission_vacancy_reservations_application_tenant"))
	private AdmissionApplication application;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admission_vacancy_reservations_year_tenant"))
	private AcademicYear academicYear;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "grade_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admission_vacancy_reservations_grade_tenant"))
	private Grade grade;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private AdmissionReservationStatus status = AdmissionReservationStatus.COMMITTED;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "reason", length = 1000)
	private String reason;

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
	}
}
