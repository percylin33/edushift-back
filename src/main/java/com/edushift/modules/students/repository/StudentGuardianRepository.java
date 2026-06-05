package com.edushift.modules.students.repository;

import com.edushift.modules.students.entity.StudentGuardian;
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
}
