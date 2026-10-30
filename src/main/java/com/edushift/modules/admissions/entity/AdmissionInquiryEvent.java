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

/** Append-only audit record for inquiry lifecycle changes. */
@Entity
@Table(name = "admission_inquiry_events", schema = "edushift",
		indexes = @Index(name = "idx_admission_inquiry_events_tenant_inquiry", columnList = "tenant_id, inquiry_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"fromStatus", "toStatus", "actorType"})
public class AdmissionInquiryEvent extends TenantAwareEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inquiry_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_admission_inquiry_events_inquiry_tenant"))
	private AdmissionInquiry inquiry;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 20)
	private AdmissionInquiryStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 20)
	private AdmissionInquiryStatus toStatus;

	@Column(name = "note", length = 2000)
	private String note;

	@Column(name = "actor_user_id", columnDefinition = "uuid")
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, length = 10)
	private ActorType actorType = ActorType.STAFF;

	public enum ActorType { SYSTEM, STAFF }
}
