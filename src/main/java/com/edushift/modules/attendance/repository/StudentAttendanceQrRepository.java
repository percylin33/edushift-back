package com.edushift.modules.attendance.repository;

import com.edushift.modules.attendance.entity.StudentAttendanceQr;
import com.edushift.modules.students.entity.Student;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link StudentAttendanceQr}
 * (Sprint 6 / BE-6.1). Tenant-scoped via Hibernate's {@code @TenantId}.
 *
 * <p>Hot path is {@link #findActiveByTokenHash(String)} which is hit
 * once per check-in. The partial unique index
 * {@code uk_qr_token_hash WHERE NOT deleted} keeps lookups O(1) over
 * the active set.
 */
@Repository
public interface StudentAttendanceQrRepository extends JpaRepository<StudentAttendanceQr, UUID> {

	/**
	 * Lookup by hash (hot path of {@code POST /attendance/check-in}).
	 * Returns active rows only — revoked or soft-deleted rows are
	 * excluded so the service can return 401 {@code QR_EXPIRED}
	 * deterministically.
	 */
	@Query("""
			select q from StudentAttendanceQr q
			where q.tokenHash = :tokenHash
			  and q.revokedAt is null
			""")
	Optional<StudentAttendanceQr> findActiveByTokenHash(@Param("tokenHash") String tokenHash);

	/**
	 * Hot-path variant of {@link #findActiveByTokenHash(String)} that
	 * eagerly hydrates the {@code student} association in the same
	 * round-trip. Used by the attendance check-in flow which always
	 * dereferences {@code qr.getStudent()} immediately after the
	 * lookup; the lazy default would force a follow-up
	 * {@code SELECT s.* FROM students WHERE id = ?} per scan.
	 */
	@Query("""
			select q from StudentAttendanceQr q
			join fetch q.student
			where q.tokenHash = :tokenHash
			  and q.revokedAt is null
			""")
	Optional<StudentAttendanceQr> findActiveByTokenHashFetchStudent(
			@Param("tokenHash") String tokenHash);

	/**
	 * Lookup by hash including revoked rows. Used in
	 * {@code POST /attendance/check-in} to distinguish between
	 * {@code QR_INVALID} (no row at all — not minted by us) and
	 * {@code QR_EXPIRED} (row exists but {@code revoked_at IS NOT NULL}).
	 */
	@Query("""
			select q from StudentAttendanceQr q
			where q.tokenHash = :tokenHash
			""")
	Optional<StudentAttendanceQr> findAnyByTokenHash(@Param("tokenHash") String tokenHash);

	/**
	 * The currently-active QR of a student. At most one due to the
	 * partial unique index {@code uk_qr_student_active}.
	 */
	@Query("""
			select q from StudentAttendanceQr q
			where q.student = :student
			  and q.revokedAt is null
			""")
	Optional<StudentAttendanceQr> findActiveByStudent(@Param("student") Student student);
}
