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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/** Public, non-binding inquiry made by a family before formal application. */
@Entity
@Table(name = "admission_inquiries", schema = "edushift",
		uniqueConstraints = @UniqueConstraint(name = "uk_admission_inquiries_public_uuid", columnNames = "public_uuid"),
		indexes = @Index(name = "idx_admission_inquiries_tenant_year_status", columnList = "tenant_id, academic_year_id, status, created_at"))
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "status", "contactType"})
@SQLDelete(sql = "UPDATE edushift.admission_inquiries SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
public class AdmissionInquiry extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_admission_inquiries_year_tenant"))
	private AcademicYear academicYear;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "grade_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_admission_inquiries_grade_tenant"))
	private Grade grade;

	@Column(name = "contact_name", nullable = false, length = 200)
	private String contactName;

	@Enumerated(EnumType.STRING)
	@Column(name = "contact_type", nullable = false, length = 10)
	private AdmissionContactType contactType;

	@Column(name = "contact_value", nullable = false, length = 254)
	private String contactValue;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 20)
	private AdmissionInquirySource source = AdmissionInquirySource.WEBSITE;

	@Enumerated(EnumType.STRING)
	@Column(name = "initial_channel", nullable = false, length = 20)
	private AdmissionInquiryChannel initialChannel = AdmissionInquiryChannel.WEBSITE;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private AdmissionInquiryStatus status = AdmissionInquiryStatus.RECEIVED;

	@Column(name = "privacy_accepted_at", nullable = false)
	private Instant privacyAcceptedAt;

	@Column(name = "privacy_notice_given_at")
	private Instant privacyNoticeGivenAt;

	@Column(name = "note", length = 2000)
	private String note;

	@Column(name = "converted_application_id", columnDefinition = "uuid")
	private UUID convertedApplicationId;

    @Column(name = "responsible_name", length = 100)
    private String responsibleName;

    @Column(name = "next_attention_date")
    private java.time.LocalDate nextAttentionDate;

    @Column(name = "next_action", length = 300)
    private String nextAction;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
		if (status == null) status = AdmissionInquiryStatus.RECEIVED;
		if (source == null) source = AdmissionInquirySource.WEBSITE;
		if (initialChannel == null) initialChannel = AdmissionInquiryChannel.WEBSITE;
	}
}
