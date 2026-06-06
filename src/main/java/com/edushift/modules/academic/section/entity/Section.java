package com.edushift.modules.academic.section.entity;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
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
 * Physical class group within a {@code (AcademicYear, Grade)} tuple
 * of a tenant (Sprint 4 / BE-4.3).
 *
 * <h3>Identity</h3>
 * {@code (academic_year_id, grade_id, lower(name))} is unique per
 * tenant on non-deleted rows
 * ({@code uk_sections_year_grade_name_active}). Hibernate's
 * {@code @TenantId} discriminator scopes everything else.
 *
 * <h3>Lifecycle</h3>
 * Sections inherit the lifecycle of their parent year:
 * <ul>
 *   <li>{@link com.edushift.modules.academic.year.entity.AcademicYearStatus#PLANNING PLANNING} /
 *       {@link com.edushift.modules.academic.year.entity.AcademicYearStatus#ACTIVE ACTIVE} —
 *       sections are fully editable.</li>
 *   <li>{@link com.edushift.modules.academic.year.entity.AcademicYearStatus#CLOSED CLOSED} —
 *       writes are rejected with {@code ACADEMIC_YEAR_LOCKED} (409). Reads
 *       remain allowed for historical analytics.</li>
 * </ul>
 *
 * <h3>Downstream</h3>
 * BE-4.8 will add {@code student_enrollments} that reference a section.
 * That dependency activates the {@code SECTION_HAS_ENROLLMENTS} error
 * contract on delete. Until then, deletes only fail when an active
 * enrollment exists at runtime — which is impossible right now.
 */
@Entity
@Table(
		name = "sections",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_sections_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_sections_tenant_year",
						columnList = "tenant_id, academic_year_id, display_order, name"),
				@Index(name = "idx_sections_year_grade_order",
						columnList = "academic_year_id, grade_id, display_order, name"),
				@Index(name = "idx_sections_tenant_grade",
						columnList = "tenant_id, grade_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "name", "displayOrder"})
@SQLDelete(sql = "UPDATE edushift.sections "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Section extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "academic_year_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_sections_academic_year"))
	private AcademicYear academicYear;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "grade_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_sections_grade"))
	private Grade grade;

	@Column(name = "name", nullable = false, length = 40)
	private String name;

	@Column(name = "capacity")
	private Integer capacity;

	@Column(name = "display_order", nullable = false)
	private Integer displayOrder = 1;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (name != null) {
			name = name.trim();
		}
		if (displayOrder == null) {
			displayOrder = 1;
		}
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
