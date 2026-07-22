package com.edushift.modules.teachers.repository;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Teacher}. Tenant-scoped via
 * Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

	Optional<Teacher> findByPublicUuid(UUID publicUuid);

	@Query("""
			select t from Teacher t
			where t.documentType = :type and lower(t.documentNumber) = lower(:number)
			""")
	Optional<Teacher> findByDocument(@Param("type") DocumentType type,
			@Param("number") String number);

	@Query("""
			select t from Teacher t
			where t.email is not null and lower(t.email) = lower(:email)
			""")
	Optional<Teacher> findByEmailIgnoreCase(@Param("email") String email);

	Optional<Teacher> findByUserId(UUID userId);

	/**
	 * Filtered + paginated list. Filters compose with AND:
	 *
	 * <ul>
	 *   <li>{@code search} — case-insensitive substring match against
	 *       {@code firstName}, {@code lastName}, {@code secondLastName},
	 *       {@code documentNumber}, {@code email}.</li>
	 *   <li>{@code employmentStatus} — exact match.</li>
	 *   <li>{@code hasUserAccount} — TRUE → only with userId, FALSE →
	 *       only without.</li>
	 * </ul>
	 */
	/**
	 * Filtered + paginated list. Filters compose with AND.
	 *
	 * <p>The {@code search} parameter is required to be non-null — the
	 * service substitutes an empty string when the caller did not pass
	 * one, which makes {@code concat('%', '', '%')} match every row
	 * (no-op behaviour). Avoiding the {@code :search is null}
	 * short-circuit sidesteps a Hibernate/PostgreSQL parameter-typing
	 * issue where a null string parameter is bound as {@code bytea}
	 * and {@code lower(bytea)} fails at runtime.</p>
	 *
	 * <p>{@code secondLastName} and {@code email} are guarded with
	 * {@code is not null} because they are nullable columns; without
	 * the guard a row with a null value would fail the {@code lower(...)}
	 * call.</p>
	 */
	@Query("""
			select t from Teacher t
			where (lower(t.firstName) like lower(concat('%', :search, '%'))
			        or lower(t.lastName) like lower(concat('%', :search, '%'))
			        or (t.secondLastName is not null
			              and lower(t.secondLastName) like lower(concat('%', :search, '%')))
			        or lower(t.documentNumber) like lower(concat('%', :search, '%'))
			        or (t.email is not null
			              and lower(t.email) like lower(concat('%', :search, '%'))))
			  and (:status is null or t.employmentStatus = :status)
			  and (:hasUserAccount is null
			        or (:hasUserAccount = true and t.userId is not null)
			        or (:hasUserAccount = false and t.userId is null))
			""")
	Page<Teacher> findFiltered(
			@Param("search") String search,
			@Param("status") EmploymentStatus status,
			@Param("hasUserAccount") Boolean hasUserAccount,
			Pageable pageable);

	/**
	 * Atomically increments {@code teachers.assignments_count} by 1 for the
	 * teacher identified by its public UUID. Used by
	 * {@code TeacherAssignmentWorkloadListener} to keep the workload
	 * counter consistent with the {@code teacher_assignments} table.
	 *
	 * <p>Returns the number of rows updated. A {@code 0} result means the
	 * teacher was not found in the current tenant scope (cross-tenant race
	 * or stale event) — the caller treats this as a no-op warning.</p>
	 */
	@Modifying
	@Query("""
			update Teacher t
			set t.assignmentsCount = t.assignmentsCount + 1
			where t.publicUuid = :publicUuid
			""")
	int incrementAssignmentsCountByPublicUuid(@Param("publicUuid") UUID publicUuid);

	/**
	 * Atomically decrements {@code teachers.assignments_count} by 1 for
	 * the teacher identified by its public UUID. Used by
	 * {@code TeacherAssignmentUnassignedWorkloadListener} (Sprint 5 /
	 * DEBT-TEA-3) when an assignment is soft-ended. Mirrors
	 * {@link #incrementAssignmentsCountByPublicUuid(UUID)}.
	 *
	 * <p>The {@code assignmentsCount > 0} guard prevents the counter from
	 * going negative on event reordering or duplicate unassigned events.
	 * The DB-level CHECK constraint {@code chk_teachers_assignments_count_nonneg}
	 * (migration V79) is the ultimate backstop; this clause keeps the
	 * generated {@code WHERE} selective so concurrent decrements cannot
	 * starve the counter below zero.</p>
	 */
	@Modifying
	@Query("""
			update Teacher t
			set t.assignmentsCount = t.assignmentsCount - 1
			where t.publicUuid = :publicUuid
			  and t.assignmentsCount > 0
			""")
	int decrementAssignmentsCountByPublicUuid(@Param("publicUuid") UUID publicUuid);
}
