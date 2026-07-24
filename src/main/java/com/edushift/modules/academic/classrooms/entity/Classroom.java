package com.edushift.modules.academic.classrooms.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Physical classroom (Sprint cierre-C / B4).
 *
 * <p>Reusable catalog entry that {@code time_slots.classroom_id}
 * references. Pre-B4 slots carry a free-text {@code classroom} string
 * (label like "Lab 2"); new slots reference this entity for proper
 * conflict detection.</p>
 *
 * <p>Multi-tenant: extends {@link TenantAwareEntity}; the partial
 * UNIQUE index {@code uk_classrooms_tenant_code} (V86) keeps the
 * {@code (tenant_id, code)} uniqueness contract without preventing
 * the same code in a different tenant.</p>
 */
@Entity
@Table(
		name = "classrooms",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_classrooms_public_uuid", columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_classrooms_tenant_active", columnList = "tenant_id, type")
		}
)
@Getter
@Setter
@NoArgsConstructor
public class Classroom extends TenantAwareEntity {

	public enum Type {
		CLASSROOM, LAB, GYM, LIBRARY, AUDITORIUM, OUTDOOR, OTHER
	}

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 40)
	private Type type = Type.CLASSROOM;

	/** Integer >= 0; null = capacidad desconocida. */
	@Column(name = "capacity")
	private Integer capacity;

	@Column(name = "location", length = 160)
	private String location;

	@Column(name = "description", length = 500)
	private String description;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
	}
}