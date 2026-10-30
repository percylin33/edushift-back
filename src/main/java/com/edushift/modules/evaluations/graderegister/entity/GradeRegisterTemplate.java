package com.edushift.modules.evaluations.graderegister.entity;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
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
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Formal grade-register template (F1).
 *
 * <p>System catalog rows ({@code isSystem=true}) are seeded per-tenant
 * without an academic year. Tenant working copies ({@code isSystem=false})
 * always bind to an {@link AcademicYear} after adopt/fork.</p>
 */
@Entity
@Table(
		name = "grade_register_templates",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_grade_register_templates_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_grt_tenant_year",
						columnList = "tenant_id, academic_year_id"),
				@Index(name = "idx_grt_tenant_system",
						columnList = "tenant_id, is_system")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "name", "scope", "isSystem", "isDefault"})
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE edushift.grade_register_templates "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class GradeRegisterTemplate extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "academic_year_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_grade_register_templates_year"))
	private AcademicYear academicYear;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "course_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_grade_register_templates_course"))
	private Course course;

	/** Set only when {@link GradeRegisterTemplateScope#LEVEL}. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "academic_level_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_grade_register_templates_level"))
	private AcademicLevel academicLevel;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_template_id", columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_grade_register_templates_source"))
	private GradeRegisterTemplate sourceTemplate;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "scope", nullable = false, length = 16)
	private GradeRegisterTemplateScope scope = GradeRegisterTemplateScope.TENANT;

	@Column(name = "is_system", nullable = false)
	private Boolean isSystem = Boolean.FALSE;

	@Column(name = "is_default", nullable = false)
	private Boolean isDefault = Boolean.FALSE;

	/** SCORE_0_20 or LITERAL_CNEB. Inherited by columns of this working copy. */
	@Enumerated(EnumType.STRING)
	@Column(name = "grading_scale", nullable = false, length = 24)
	private EvaluationScale gradingScale = EvaluationScale.SCORE_0_20;

	/** Stable seed key for idempotent catalog materialization. */
	@Column(name = "catalog_code", length = 40)
	private String catalogCode;

	/**
	 * Catalog hint: {@code INICIAL} | {@code PRIMARIA} | {@code SECUNDARIA}.
	 * Empty for tenant working copies.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recommended_levels", nullable = false, columnDefinition = "jsonb")
	private List<String> recommendedLevels = List.of();

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (scope == null) {
			scope = GradeRegisterTemplateScope.TENANT;
		}
		if (isSystem == null) {
			isSystem = Boolean.FALSE;
		}
		if (isDefault == null) {
			isDefault = Boolean.FALSE;
		}
		if (gradingScale == null) {
			gradingScale = EvaluationScale.SCORE_0_20;
		}
		if (recommendedLevels == null) {
			recommendedLevels = List.of();
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
