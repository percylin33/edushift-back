package com.edushift.modules.students.repository;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Student;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link Student}.
 *
 * <p>All read/write paths are tenant-scoped automatically through
 * Hibernate's {@code @TenantId} discriminator — there is no global
 * variant for this aggregate (students never need a cross-tenant
 * lookup the way invitations do).
 *
 * <p>Filter composition uses
 * {@link JpaSpecificationExecutor} (same pattern as
 * {@code UserRepository}). Specifications keep the filter logic
 * testable without growing a custom query for every dimension we
 * decide to expose to the list endpoint.
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, UUID>,
		JpaSpecificationExecutor<Student> {

	Optional<Student> findByPublicUuid(UUID publicUuid);

	/**
	 * Pre-check before insert / update: enforces the natural-identity
	 * uniqueness ({@code documentType + documentNumber}) per tenant.
	 * The DB partial unique index is the belt-and-suspenders guarantee;
	 * the pre-check makes the conflict error message actionable.
	 */
	Optional<Student> findByDocumentTypeAndDocumentNumber(
			DocumentType documentType, String documentNumber);

	/**
	 * Pre-check for the optional email uniqueness (case-insensitive,
	 * non-deleted only — the partial unique index in V10 enforces this
	 * at the DB level).
	 */
	Optional<Student> findByEmailIgnoreCase(String email);

	/**
	 * Resolves the {@link Student} record (if any) linked to the
	 * given auth {@code userId} (FK {@code students.user_id}
	 * → {@code users.public_uuid}). Used by BE-7b.2 to bridge the
	 * quiz attempt's {@code student_user_id} (which is a
	 * {@code users.public_uuid}) to the enrollment lookup
	 * {@link com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository#existsActiveAt}
	 * (which expects a {@link Student} entity).
	 *
	 * <p>Returns empty when the user has no student record
	 * (e.g. it's a parent or a teacher), in which case the
	 * caller should treat the enrollment check as failed.
	 */
	Optional<Student> findByUserId(UUID userId);

	/**
	 * Tenant-wide count used by the SUPER_ADMIN tenants panel
	 * (Sprint 16 / hardening). Spring Data JPA derives the query from
	 * the method name; the {@code tenant_id} filter is explicit so the
	 * call works regardless of the current {@link com.edushift.shared.multitenancy.TenantContext}
	 * binding — required because the admin list runs under
	 * {@code SUPER_ADMIN_SENTINEL} which bypasses Hibernate's auto-filter.
	 */
	long countByTenantId(UUID tenantId);

	/**
	 * DEBT-STUDENT-PRIVACY (Fase 0.2): resolve the {@code Student.publicUuid}
	 * linked to a given {@code users.publicUuid}. Used by the payments module
	 * to widen the ownership check from
	 * {@code invoices.guardian_user_id = me} to
	 * {@code (invoices.guardian_user_id = me OR invoices.student_id = me.studentPublicUuid)},
	 * so a STUDENT whose parents pay their invoices can still see them
	 * in their personal "Mis pagos" view.
	 *
	 * <p>Returns empty if the user has no student record (PARENT, TEACHER,
	 * etc.) — callers should treat that as "no student-side invoices".</p>
	 */
	default Optional<UUID> findPublicUuidByUserId(UUID userId) {
		return findByUserId(userId).map(com.edushift.modules.students.entity.Student::getPublicUuid);
	}

}
