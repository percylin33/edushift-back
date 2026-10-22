package com.edushift.modules.evaluations.competencyresult.entity;

import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.students.entity.Student;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Competency evidence recorded for one student in one evaluation. */
@Entity
@Table(name = "student_competency_results", schema = "edushift",
		uniqueConstraints = @UniqueConstraint(name = "uk_student_competency_result_public_uuid",
				columnNames = "public_uuid"),
		indexes = @Index(name = "idx_student_competency_results_eval_student",
				columnList = "tenant_id, evaluation_id, student_id, is_active"))
@Getter
@Setter
@NoArgsConstructor
public class StudentCompetencyResult extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "evaluation_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_student_competency_results_evaluation"))
	private Evaluation evaluation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_student_competency_results_student"))
	private Student student;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "competency_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_student_competency_results_competency"))
	private Competency competency;

	@Column(name = "achievement_level", nullable = false, length = 32)
	private String achievementLevel;

	@Column(name = "evidence_summary", columnDefinition = "text")
	private String evidenceSummary;

	@Column(name = "descriptive_conclusion", columnDefinition = "text")
	private String descriptiveConclusion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "criteria_results", nullable = false, columnDefinition = "jsonb")
	private List<Map<String, String>> criteriaResults = List.of();

	@Column(name = "revision", nullable = false)
	private Integer revision = 1;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "recorded_by_user_id", nullable = false, columnDefinition = "uuid")
	private UUID recordedByUserId;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = Boolean.TRUE;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
		if (recordedAt == null) recordedAt = Instant.now();
		if (isActive == null) isActive = Boolean.TRUE;
		if (revision == null) revision = 1;
		if (criteriaResults == null) criteriaResults = List.of();
	}
}
