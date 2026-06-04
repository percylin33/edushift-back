package com.edushift.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Multi-tenant entity: scoped to a single tenant (school / institution).
 * <p>
 * The {@code tenant_id} column is managed by Hibernate's discriminator
 * multi-tenancy: when {@code TenantContext} is set, queries on this entity
 * are auto-filtered ({@code WHERE tenant_id = :currentTenant}) and INSERTs
 * auto-populate {@code tenant_id} with the current value.
 * <p>
 * The field is immutable after creation (a record cannot move between tenants).
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantAwareEntity extends AuditableEntity {

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID tenantId;

}
