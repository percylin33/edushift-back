package com.edushift.modules.admissions.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Immutable audit entry for an application status transition. */
@Entity
@Table(name = "admission_status_events", schema = "edushift",
		indexes = @Index(name = "idx_admission_status_events_tenant_application", columnList = "tenant_id, application_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"fromStatus", "toStatus"})
public class AdmissionStatusEvent extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_admission_status_events_application"))
	private AdmissionApplication application;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 20)
	private AdmissionApplicationStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 20)
	private AdmissionApplicationStatus toStatus;

	@Column(name = "note", length = 2000)
	private String note;

	@Column(name = "actor_user_id", columnDefinition = "uuid")
	private UUID actorUserId;
}
