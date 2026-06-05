package com.edushift.modules.students.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/**
 * A student enrolled at a tenant institution.
 *
 * <h3>Identifiers</h3>
 * <ul>
 *   <li>{@code id} (inherited) — internal UUIDv7, FK target inside the DB.</li>
 *   <li>{@code publicUuid} — UUIDv4 surfaced via REST.</li>
 *   <li>{@code documentType + documentNumber} — natural identity in the
 *       admin domain. The combination is unique per tenant on
 *       non-deleted rows (partial unique index in the migration).</li>
 * </ul>
 *
 * <h3>Email semantics</h3>
 * Optional. When present it is normalised lowercase on every
 * persist/update and unique per tenant on non-deleted rows. Email and
 * phone are <em>not</em> required because young children rarely have
 * either of their own — guardian contact lives in the {@code guardians}
 * table (Sprint 3.4).
 *
 * <h3>{@code metadata}</h3>
 * Free-form jsonb map for institution-specific extension fields
 * (allergies, blood type, custom flags). Same pattern as
 * {@code Tenant.settings}: schema-less, so adding a new field doesn't
 * require a migration. The contract with consumers is enforced at the
 * DTO layer, not here.
 *
 * <h3>{@code userId}</h3>
 * Optional FK to {@code auth.users} — set when the student also has an
 * account in the system. Sprint 3 leaves it nullable for everyone.
 */
@Entity
@Table(
		name = "students",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_students_public_uuid", columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_students_tenant_enrollment_status",
						columnList = "tenant_id, enrollment_status"),
				@Index(name = "idx_students_user_id", columnList = "user_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "documentType", "documentNumber", "firstName", "lastName"})
@SQLDelete(sql = "UPDATE edushift.students "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Student extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	// ---------------------------------------------------------------------------
	// Identity
	// ---------------------------------------------------------------------------

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 20)
	private DocumentType documentType;

	@Column(name = "document_number", nullable = false, length = 20)
	private String documentNumber;

	// ---------------------------------------------------------------------------
	// Profile
	// ---------------------------------------------------------------------------

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "second_last_name", length = 100)
	private String secondLastName;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, length = 20)
	private Gender gender = Gender.NOT_SPECIFIED;

	// ---------------------------------------------------------------------------
	// Contact (all optional)
	// ---------------------------------------------------------------------------

	@Column(name = "email", length = 254)
	private String email;

	@Column(name = "phone", length = 32)
	private String phone;

	@Column(name = "address", length = 500)
	private String address;

	// ---------------------------------------------------------------------------
	// Enrollment lifecycle (institution-scoped, not academic-year-scoped)
	// ---------------------------------------------------------------------------

	@Enumerated(EnumType.STRING)
	@Column(name = "enrollment_status", nullable = false, length = 20)
	private EnrollmentStatus enrollmentStatus = EnrollmentStatus.PENDING;

	@Column(name = "enrollment_date")
	private LocalDate enrollmentDate;

	// ---------------------------------------------------------------------------
	// Optional cross-module link
	// ---------------------------------------------------------------------------

	@Column(name = "user_id", columnDefinition = "uuid")
	private UUID userId;

	// ---------------------------------------------------------------------------
	// Free-form extension fields
	// ---------------------------------------------------------------------------

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> metadata = new HashMap<>();

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (gender == null) {
			gender = Gender.NOT_SPECIFIED;
		}
		if (enrollmentStatus == null) {
			enrollmentStatus = EnrollmentStatus.PENDING;
		}
		if (metadata == null) {
			metadata = new HashMap<>();
		}
		normalizeEmail();
	}

	@PreUpdate
	private void onPreUpdate() {
		normalizeEmail();
	}

	private void normalizeEmail() {
		if (email != null) {
			email = email.trim();
			if (email.isEmpty()) {
				email = null;
			}
			else {
				email = email.toLowerCase();
			}
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

	// ---------------------------------------------------------------------------
	// Convenience
	// ---------------------------------------------------------------------------

	public String fullName() {
		StringBuilder sb = new StringBuilder();
		if (firstName != null) sb.append(firstName);
		if (lastName != null) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(lastName);
		}
		if (secondLastName != null) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(secondLastName);
		}
		return sb.toString();
	}
}
