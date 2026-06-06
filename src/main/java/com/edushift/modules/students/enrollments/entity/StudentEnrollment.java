package com.edushift.modules.students.enrollments.entity;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.students.entity.Student;
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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * Per-year placement of a {@link Student} into a {@link Section}.
 * Sprint 4 / BE-4.8.
 *
 * <h3>Lifecycle</h3>
 * Driven by {@link StudentEnrollmentStatus}:
 * <ul>
 *   <li><strong>ACTIVE</strong> — current row. {@code withdrawnAt is null}.
 *       The DB partial unique index {@code uk_student_enrollments_active}
 *       allows only one active row per {@code (student, academic_year)}.</li>
 *   <li><strong>Terminal</strong> ({@code WITHDRAWN | TRANSFERRED |
 *       GRADUATED}) — soft-ended. {@code withdrawnAt} is set, the row is
 *       preserved for transcripts / audit, and a brand-new ACTIVE row can
 *       re-use the same {@code (student, year)} pair (e.g. mid-year
 *       transfer to another section).</li>
 *   <li><strong>Soft-deleted</strong> — administrative removal via the
 *       {@code deleted} flag inherited from {@code BaseEntity}.</li>
 * </ul>
 *
 * <h3>Identity at the application layer</h3>
 * The unique partial index forces "one ACTIVE per (student, year)" but
 * the service still validates explicitly so the API can return a useful
 * {@code STUDENT_ALREADY_ENROLLED} (409) instead of a generic constraint
 * violation. Race conditions still surface a {@code DataIntegrityViolation}
 * which is mapped back to the same code.
 */
@Entity
@Table(
		name = "student_enrollments",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_student_enrollments_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_student_enrollments_section_status",
						columnList = "section_id, status"),
				@Index(name = "idx_student_enrollments_student_enrolled_at",
						columnList = "student_id, enrolled_at"),
				@Index(name = "idx_student_enrollments_active_lookup",
						columnList = "student_id, academic_year_id, section_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "status", "enrolledAt", "withdrawnAt"})
@SQLDelete(sql = "UPDATE edushift.student_enrollments "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class StudentEnrollment extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_student_enrollments_student"))
	private Student student;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_student_enrollments_section"))
	private Section section;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_student_enrollments_year"))
	private AcademicYear academicYear;

	@Column(name = "enrolled_at", nullable = false)
	private LocalDate enrolledAt;

	@Column(name = "withdrawn_at")
	private LocalDate withdrawnAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private StudentEnrollmentStatus status = StudentEnrollmentStatus.ACTIVE;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = StudentEnrollmentStatus.ACTIVE;
		}
	}

	/**
	 * Convenience: the row currently counts against the active-uniqueness
	 * index. The {@code deleted} flag from {@code BaseEntity} is filtered
	 * out by Hibernate so reads never see administratively removed rows.
	 */
	public boolean isActive() {
		return status == StudentEnrollmentStatus.ACTIVE;
	}

	@Override
	public void markDeleted() {
		super.markDeleted();
		this.deletedAt = Instant.now();
	}

	@Override
	public void restore() {
		super.restore();
		this.deletedAt = null;
	}
}
