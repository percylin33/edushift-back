package com.edushift.modules.audit.entity;

import com.edushift.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistent audit record. Append-only by design — never updated after creation.
 * <p>
 * Indexed by {@code (tenant_id, occurred_at)} and {@code (actor_id, occurred_at)}
 * for activity timeline queries.
 */
@Entity
@Table(name = "audit_logs", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

	@Column(name = "tenant_id", columnDefinition = "uuid")
	private UUID tenantId;

	@Column(name = "actor_id", columnDefinition = "uuid")
	private UUID actorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 50)
	private com.edushift.modules.audit.events.AuditAction action;

	@Column(name = "resource_type", length = 100)
	private String resourceType;

	@Column(name = "resource_id", columnDefinition = "uuid")
	private UUID resourceId;

	@Column(name = "summary", columnDefinition = "text")
	private String summary;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", columnDefinition = "jsonb")
	private Map<String, Object> metadata;

	@Column(name = "trace_id", length = 64)
	private String traceId;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

}
