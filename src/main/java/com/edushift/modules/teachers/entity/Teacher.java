package com.edushift.modules.teachers.entity;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * Teaching staff at a tenant institution. Sprint 4 / BE-4.6.
 *
 * <h3>Identity</h3>
 * <ul>
 *   <li>{@code documentType + documentNumber} — natural identity inside
 *       the tenant. Unique on non-deleted rows.</li>
 *   <li>{@code email} — optional; when present, unique per tenant on
 *       non-deleted rows (lowercased on persist/update).</li>
 * </ul>
 *
 * <h3>{@code userId}</h3>
 * Optional FK to {@code auth.users.id}. Set in two flows:
 * <ol>
 *   <li><strong>Link-user</strong> ({@code POST /teachers/{uuid}/link-user})
 *       — admin links an existing User account to this teacher.</li>
 *   <li><strong>Invite + accept</strong> ({@code POST /teachers/{uuid}/invite})
 *       — creates a {@code user_invitations} row with
 *       {@code metadata.teacherId}; on accept, the
 *       {@code TeacherInvitationListener} populates {@code userId}
 *       atomically with the new User.</li>
 * </ol>
 * The unique partial index {@code uk_teachers_user_active} guarantees
 * a User cannot be linked to two Teachers (constraint
 * {@code USER_ALREADY_LINKED_TO_TEACHER}).
 *
 * <h3>Reused enums</h3>
 * {@link DocumentType} and {@link Gender} live in
 * {@code modules.students.entity} — Sprint 3 already shaped them around
 * Peruvian institutions and they apply verbatim here. The
 * teacher-specific lifecycle ({@link EmploymentStatus}) lives in this
 * package.
 */
@Entity
@Table(
		name = "teachers",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_teachers_public_uuid", columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_teachers_tenant_employment_status",
						columnList = "tenant_id, employment_status")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "documentType", "documentNumber", "firstName", "lastName"})
@SQLDelete(sql = "UPDATE edushift.teachers "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Teacher extends TenantAwareEntity {

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
	// Contact
	// ---------------------------------------------------------------------------

	@Column(name = "email", length = 254)
	private String email;

	@Column(name = "phone", length = 32)
	private String phone;

	// ---------------------------------------------------------------------------
	// Teacher-specific
	// ---------------------------------------------------------------------------

	@Column(name = "title", length = 50)
	private String title;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "specializations", nullable = false, columnDefinition = "jsonb")
	private List<String> specializations = new ArrayList<>();

	@Column(name = "hire_date")
	private LocalDate hireDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "employment_status", nullable = false, length = 20)
	private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

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
		if (employmentStatus == null) {
			employmentStatus = EmploymentStatus.ACTIVE;
		}
		if (specializations == null) {
			specializations = new ArrayList<>();
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
