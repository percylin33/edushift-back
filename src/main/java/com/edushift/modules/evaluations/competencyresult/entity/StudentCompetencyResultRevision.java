package com.edushift.modules.evaluations.competencyresult.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable snapshot of a student competency result revision. */
@Entity
@Table(name = "student_competency_result_revisions", schema = "edushift",
		uniqueConstraints = @UniqueConstraint(name = "uk_student_competency_result_revision",
				columnNames = {"result_id", "revision"}))
@Getter
@Setter
@NoArgsConstructor
public class StudentCompetencyResultRevision extends TenantAwareEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "result_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_student_competency_result_revisions_result"))
	private StudentCompetencyResult result;

	@Column(name = "revision", nullable = false)
	private Integer revision;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> snapshot;

	@Column(name = "changed_by_user_id", nullable = false, columnDefinition = "uuid")
	private UUID changedByUserId;

	@Column(name = "changed_at", nullable = false)
	private Instant changedAt;
}
