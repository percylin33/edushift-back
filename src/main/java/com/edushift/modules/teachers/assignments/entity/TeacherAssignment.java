package com.edushift.modules.teachers.assignments.entity;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * M:N pivot between {@link Teacher} and {@code (Section, Course, AcademicPeriod)}.
 * Sprint 4 / BE-4.7.
 *
 * <h3>Lifecycle (soft-end)</h3>
 * An assignment goes through three phases:
 * <ul>
 *   <li><strong>Active</strong> — {@code unassignedAt == null}. Counts
 *       against the {@code uk_teacher_assignments_active} unique
 *       partial index (one active row per
 *       {@code (teacher, section, course, period)} tuple).</li>
 *   <li><strong>Soft-ended</strong> — {@code unassignedAt} is set, the
 *       row is preserved for grade reports / audit. A new active row
 *       can re-use the same tuple immediately.</li>
 *   <li><strong>Soft-deleted</strong> — flagged through {@code deleted}
 *       inherited from {@code BaseEntity}; never used in normal
 *       operation, kept as a backstop for accidental writes.</li>
 * </ul>
 *
 * <h3>Identity at the application layer</h3>
 * The unique partial index forces "one active assignment per tuple"
 * but the service still validates explicitly so the API can return a
 * useful {@code ASSIGNMENT_ALREADY_ACTIVE} (409) instead of a generic
 * constraint violation.
 */
@Entity
@Table(
		name = "teacher_assignments",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_teacher_assignments_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_teacher_assignments_teacher_period_active",
						columnList = "teacher_id, academic_period_id"),
				@Index(name = "idx_teacher_assignments_section_period_active",
						columnList = "section_id, academic_period_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "assignedAt", "unassignedAt"})
@SQLDelete(sql = "UPDATE edushift.teacher_assignments "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class TeacherAssignment extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_teacher_assignments_teacher"))
	private Teacher teacher;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "section_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_teacher_assignments_section"))
	private Section section;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_teacher_assignments_course"))
	private Course course;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_period_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_teacher_assignments_period"))
	private AcademicPeriod academicPeriod;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	@Column(name = "unassigned_at")
	private Instant unassignedAt;

	@Column(name = "notes", columnDefinition = "text")
	private String notes;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (assignedAt == null) {
			assignedAt = Instant.now();
		}
	}

	/**
	 * Convenience: an assignment is active when not soft-ended.
	 * The {@code deleted} flag from {@code BaseEntity} is filtered by
	 * Hibernate's @SQLRestriction at the type level so it never reaches
	 * read paths.
	 */
	public boolean isActive() {
		return unassignedAt == null;
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
