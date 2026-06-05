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

}
