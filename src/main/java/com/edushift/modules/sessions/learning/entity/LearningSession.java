package com.edushift.modules.sessions.learning.entity;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/**
 * Daily occurrence of a {@link TeacherAssignment} on a particular
 * {@code scheduled_date} (Sprint 5A / BE-5A.4).
 *
 * <p>Anchors a {@link Unit} from the same course as the assignment and
 * optionally references the {@link Competency} / {@link Capacity}
 * targets being worked on. The pedagogical content lives in a free-form
 * {@link SessionContent} blob persisted as Postgres {@code jsonb}.</p>
 *
 * <h3>Lifecycle</h3>
 * Managed by the {@link SessionStatus} state machine. Both the entity
 * and the service throw on illegal transitions, and the database
 * enforces timestamp coherence via {@code chk_learning_sessions_status_timestamps}.
 *
 * <h3>Concurrency</h3>
 * The {@link Version} column makes lifecycle endpoints
 * race-safe ({@code start} / {@code complete} / {@code cancel} attach
 * the loaded version to the payload).
 */
@Entity
@Table(
		name = "learning_sessions",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_learning_sessions_public_uuid",
						columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_learning_sessions_tenant_assignment_date",
						columnList = "tenant_id, teacher_assignment_id, scheduled_date"),
				@Index(name = "idx_learning_sessions_tenant_unit_date",
						columnList = "tenant_id, unit_id, scheduled_date"),
				@Index(name = "idx_learning_sessions_tenant_status_date",
						columnList = "tenant_id, status, scheduled_date")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "title", "scheduledDate", "status"})
@SQLDelete(sql = "UPDATE edushift.learning_sessions "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class LearningSession extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_assignment_id", nullable = false,
			columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_learning_sessions_assignment"))
	private TeacherAssignment teacherAssignment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "unit_id", nullable = false, columnDefinition = "uuid",
			foreignKey = @ForeignKey(name = "fk_learning_sessions_unit"))
	private Unit unit;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "objective", columnDefinition = "text")
	private String objective;

	@Column(name = "scheduled_date", nullable = false)
	private LocalDate scheduledDate;

	@Column(name = "duration_minutes", nullable = false)
	private Integer durationMinutes;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "content", columnDefinition = "jsonb")
	private SessionContent content;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private SessionStatus status = SessionStatus.PLANNED;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	// Many-to-many: competencies referenced by this session.
	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
			schema = "edushift",
			name = "learning_session_competencies",
			joinColumns = @JoinColumn(name = "learning_session_id",
					foreignKey = @ForeignKey(name = "fk_lsc_session")),
			inverseJoinColumns = @JoinColumn(name = "competency_id",
					foreignKey = @ForeignKey(name = "fk_lsc_competency"))
	)
	private Set<Competency> competencies = new HashSet<>();

	// Many-to-many: capacities referenced by this session.
	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
			schema = "edushift",
			name = "learning_session_capacities",
			joinColumns = @JoinColumn(name = "learning_session_id",
					foreignKey = @ForeignKey(name = "fk_lscap_session")),
			inverseJoinColumns = @JoinColumn(name = "capacity_id",
					foreignKey = @ForeignKey(name = "fk_lscap_capacity"))
	)
	private Set<Capacity> capacities = new HashSet<>();

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = SessionStatus.PLANNED;
		}
		normalise();
	}

	@PreUpdate
	private void onPreUpdate() {
		normalise();
	}

	private void normalise() {
		if (title != null) {
			title = title.trim();
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
