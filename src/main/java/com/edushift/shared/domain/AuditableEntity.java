package com.edushift.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * Entity tracked by the user who created and last modified it.
 * <p>
 * {@code created_by} / {@code updated_by} are populated automatically from
 * {@code AuditorAware<UUID>} (see {@code JpaAuditingConfiguration}).
 * Both are nullable to allow system-generated records.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

	@CreatedBy
	@Column(name = "created_by", updatable = false, columnDefinition = "uuid")
	private UUID createdBy;

	@LastModifiedBy
	@Column(name = "updated_by", columnDefinition = "uuid")
	private UUID updatedBy;

}
