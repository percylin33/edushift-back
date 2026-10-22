package com.edushift.modules.admissions.entity;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
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

/** A student's application for one grade in one school year. */
@Entity
@Table(name = "admission_applications", schema = "edushift",
		uniqueConstraints = @UniqueConstraint(name = "uk_admission_applications_public_uuid", columnNames = "public_uuid"),
		indexes = {
				@Index(name = "idx_admission_applications_tenant_year_status", columnList = "tenant_id, academic_year_id, status, created_at"),
				@Index(name = "idx_admission_applications_tenant_year_grade", columnList = "tenant_id, academic_year_id, grade_id, status")
		})
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "status", "source"})
@SQLDelete(sql = "UPDATE edushift.admission_applications "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
public class AdmissionApplication extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_admission_applications_year"))
	private AcademicYear academicYear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_admission_applications_student"))
	private Student student;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inquiry_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_admission_applications_inquiry_tenant"))
	private AdmissionInquiry inquiry;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "grade_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_admission_applications_grade"))
	private Grade grade;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private AdmissionApplicationStatus status = AdmissionApplicationStatus.RECEIVED;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 20)
	private AdmissionSource source = AdmissionSource.IN_PERSON;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "last_status_note", length = 2000)
	private String lastStatusNote;

	@Column(name = "enrollment_public_uuid", columnDefinition = "uuid")
	private UUID enrollmentPublicUuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "student_document_type", length = 20)
	private DocumentType studentDocumentType;

	@Column(name = "student_document_number", length = 20)
	private String studentDocumentNumber;

	@Column(name = "student_first_name", length = 100)
	private String studentFirstName;

	@Column(name = "student_last_name", length = 100)
	private String studentLastName;

	@Column(name = "student_second_last_name", length = 100)
	private String studentSecondLastName;

	@Column(name = "student_birth_date")
	private java.time.LocalDate studentBirthDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "student_gender", length = 20)
	private Gender studentGender;

	@Enumerated(EnumType.STRING)
	@Column(name = "guardian_document_type", length = 20)
	private DocumentType guardianDocumentType;

	@Column(name = "guardian_document_number", length = 20)
	private String guardianDocumentNumber;

	@Column(name = "guardian_first_name", length = 100)
	private String guardianFirstName;

	@Column(name = "guardian_last_name", length = 100)
	private String guardianLastName;

	@Column(name = "guardian_email", length = 254)
	private String guardianEmail;

	@Column(name = "guardian_phone", length = 32)
	private String guardianPhone;

	@Enumerated(EnumType.STRING)
	@Column(name = "guardian_relationship", length = 20)
	private RelationshipType guardianRelationship;

	@Enumerated(EnumType.STRING)
	@Column(name = "offer_status", nullable = false, length = 20)
	private AdmissionOfferStatus offerStatus = AdmissionOfferStatus.NOT_REQUIRED;

	@Column(name = "offer_token_hash", length = 64)
	private String offerTokenHash;

	@Column(name = "offer_sent_at")
	private Instant offerSentAt;

	@Column(name = "offer_response_at")
	private Instant offerResponseAt;

	@Column(name = "offer_reminder_sent_at")
	private Instant offerReminderSentAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
		if (status == null) status = AdmissionApplicationStatus.RECEIVED;
		if (source == null) source = AdmissionSource.IN_PERSON;
		if (submittedAt == null) submittedAt = Instant.now();
	}

	@Override
	public void markDeleted() {
		super.markDeleted();
		deletedAt = Instant.now();
	}
}
