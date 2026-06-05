package com.edushift.modules.students.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * Many-to-many link between a {@link Student} and a {@link Guardian},
 * carrying relationship metadata (kind of relationship, primary contact
 * flag, pickup permission).
 *
 * <h3>Why a JPA entity and not just a join table</h3>
 * The link carries data ({@code relationship}, {@code isPrimaryContact},
 * {@code canPickupStudent}) that the application needs to read and
 * write independently. A bare {@code @ManyToMany} join table would
 * make those columns awkward to access; a first-class entity is
 * straightforward.
 *
 * <h3>Soft-delete = "unlink"</h3>
 * Removing a guardian from a student soft-deletes this row. The
 * guardian itself stays untouched (they may still be linked to a
 * sibling). Re-linking after unlink is allowed because the partial
 * unique index on the table only considers non-deleted rows.
 */
@Entity
@Table(
		name = "student_guardians",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_student_guardians_public_uuid", columnNames = "public_uuid")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "relationship", "isPrimaryContact", "canPickupStudent"})
@SQLDelete(sql = "UPDATE edushift.student_guardians "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class StudentGuardian extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false, updatable = false,
			foreignKey = @jakarta.persistence.ForeignKey(name = "fk_student_guardians_student"))
	private Student student;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "guardian_id", nullable = false, updatable = false,
			foreignKey = @jakarta.persistence.ForeignKey(name = "fk_student_guardians_guardian"))
	private Guardian guardian;

	@Enumerated(EnumType.STRING)
	@Column(name = "relationship", nullable = false, length = 20)
	private RelationshipType relationship;

	@Column(name = "is_primary_contact", nullable = false)
	private boolean isPrimaryContact = false;

	@Column(name = "can_pickup_student", nullable = false)
	private boolean canPickupStudent = false;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
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
