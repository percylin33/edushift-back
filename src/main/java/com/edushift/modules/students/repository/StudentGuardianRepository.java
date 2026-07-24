package com.edushift.modules.students.repository;

import com.edushift.modules.students.entity.StudentGuardian;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link StudentGuardian}.
 *
 * <p>Tenant-scoped automatically. The custom queries here are framed in
 * terms of the student / guardian entity ids (UUIDv7 internal PKs)
 * because the service layer already resolves the public UUIDs at the
 * boundary, and JPQL stays uniformly fast on indexed FK columns.
 */
@Repository
public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, UUID> {

	Optional<StudentGuardian> findByPublicUuid(UUID publicUuid);

	@Query("""
			select sg from StudentGuardian sg
			join fetch sg.guardian g
			where sg.student.id = :studentId
			""")
	List<StudentGuardian> findActiveByStudentId(@Param("studentId") UUID studentId);

	@Query("""
			select sg from StudentGuardian sg
			where sg.student.id = :studentId
			  and sg.guardian.id = :guardianId
			""")
	Optional<StudentGuardian> findActivePair(
			@Param("studentId") UUID studentId,
			@Param("guardianId") UUID guardianId);

	@Query("""
			select count(sg) from StudentGuardian sg
			where sg.student.id = :studentId
			  and sg.isPrimaryContact = true
			""")
	long countActivePrimaryContacts(@Param("studentId") UUID studentId);

	/**
	 * Sprint 9A / BE-9A.1 — bulk lookup of (student, primary-guardian-with-userId)
	 * pairs for a list of student internal ids. Returns only rows where
	 * the student has an ACTIVE link to a guardian whose own {@code user_id}
	 * is populated (i.e. the guardian has a portal login). Rows are ordered
	 * with the primary contact first so the publisher picks the same
	 * contact regardless of insertion order.
	 *
	 * <p>Used by {@code AttendanceServiceImpl.closeSession} to dispatch
	 * {@code STUDENT_ABSENT} notifications to the right parent in a
	 * single round-trip (no N+1).</p>
	 */
	@Query("""
			select sg from StudentGuardian sg
			join fetch sg.guardian g
			where sg.student.id in :studentIds
			  and g.userId is not null
			order by sg.isPrimaryContact desc, sg.createdAt asc
			""")
	List<StudentGuardian> findActiveByStudentIdsWithLinkedGuardian(
			@Param("studentIds") Collection<UUID> studentIds);
}
