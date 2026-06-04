package com.edushift.shared.domain;

import com.edushift.shared.identifier.UuidV7Id;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Root mapped superclass for all persistent entities.
 * <p>
 * Provides:
 * <ul>
 *   <li><strong>UUIDv7 primary key</strong> (RFC 9562, time-ordered) generated
 *       by {@link UuidV7Id} — microservice-ready, index-friendly, no DB sequence</li>
 *   <li>{@code created_at} / {@code updated_at} populated by Spring Data JPA Auditing</li>
 *   <li>{@code deleted} flag with global {@code @SQLRestriction("deleted = false")}
 *       filter — soft delete is on by default for every subclass</li>
 * </ul>
 *
 * <h3>Reusable entity template</h3>
 * <p>
 * Copy and adapt this template for new entities. The base superclass already
 * provides id, timestamps, soft-delete flag and (in {@code AuditableEntity} /
 * {@code TenantAwareEntity}) the audit and tenant columns.
 *
 * <pre>{@code
 * @Entity
 * @Table(name = "students", schema = "edushift", indexes = {
 *         @Index(name = "idx_students_tenant", columnList = "tenant_id"),
 *         @Index(name = "idx_students_email",  columnList = "email", unique = true)
 * })
 * @Getter
 * @Setter
 * @NoArgsConstructor
 * @SQLDelete(sql = "UPDATE edushift.students " +
 *                  "SET deleted = true, updated_at = NOW() WHERE id = ?")
 * public class Student extends TenantAwareEntity {
 *
 *     @Column(name = "first_name", nullable = false, length = 100)
 *     private String firstName;
 *
 *     @Column(name = "last_name", nullable = false, length = 100)
 *     private String lastName;
 *
 *     @Column(name = "email", nullable = false, length = 255)
 *     private String email;
 * }
 * }</pre>
 *
 * Matching Flyway migration:
 * <pre>{@code
 * CREATE TABLE edushift.students (
 *     id          uuid         PRIMARY KEY,
 *     tenant_id   uuid         NOT NULL,
 *     created_at  timestamptz  NOT NULL,
 *     updated_at  timestamptz  NOT NULL,
 *     created_by  uuid,
 *     updated_by  uuid,
 *     deleted     boolean      NOT NULL DEFAULT false,
 *     first_name  varchar(100) NOT NULL,
 *     last_name   varchar(100) NOT NULL,
 *     email       varchar(255) NOT NULL
 * );
 * CREATE INDEX idx_students_tenant ON edushift.students (tenant_id) WHERE NOT deleted;
 * CREATE TRIGGER set_updated_at_students BEFORE UPDATE ON edushift.students
 *     FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
 * }</pre>
 */
@Getter
@Setter
@ToString(of = "id")
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = false")
public abstract class BaseEntity {

	@Id
	@UuidV7Id
	@Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID id;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted", nullable = false)
	private boolean deleted = false;

	/** Marks this managed entity as soft-deleted. */
	public void markDeleted() {
		this.deleted = true;
	}

	/** Restores a soft-deleted entity (must be loaded via native query first). */
	public void restore() {
		this.deleted = false;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BaseEntity that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
