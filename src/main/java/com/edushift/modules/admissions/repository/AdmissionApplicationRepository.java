package com.edushift.modules.admissions.repository;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.admissions.entity.AdmissionApplication;
import com.edushift.modules.admissions.entity.AdmissionApplicationStatus;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.DocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

@Repository
public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplication, UUID> {

	Optional<AdmissionApplication> findByPublicUuid(UUID publicUuid);

	@Query("select a from AdmissionApplication a where a.id = :id")
	Optional<AdmissionApplication> findScopedById(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AdmissionApplication a where a.publicUuid = :publicUuid")
	Optional<AdmissionApplication> findByPublicUuidForUpdate(@Param("publicUuid") UUID publicUuid);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AdmissionApplication a where a.offerTokenHash = :hash")
	Optional<AdmissionApplication> findByOfferTokenHashForUpdate(@Param("hash") String hash);


	@Query("""
			select a from AdmissionApplication a
			left join fetch a.student
			join fetch a.grade
			join fetch a.academicYear
			where a.academicYear = :year
			order by a.submittedAt desc
			""")
	List<AdmissionApplication> findAllForYear(@Param("year") AcademicYear academicYear);

	boolean existsByAcademicYearAndStudent(AcademicYear academicYear, Student student);

	boolean existsByAcademicYearAndStudentDocumentTypeAndStudentDocumentNumber(
			AcademicYear academicYear, DocumentType studentDocumentType, String studentDocumentNumber);

	long countByAcademicYearAndGradeAndStatusAndEnrollmentPublicUuidIsNull(
			AcademicYear academicYear, Grade grade, AdmissionApplicationStatus status);

	@Query("""
			select count(a) from AdmissionApplication a
			where a.academicYear = :year
			  and a.grade = :grade
			  and a.status = com.edushift.modules.admissions.entity.AdmissionApplicationStatus.ADMITTED
			  and a.enrollmentPublicUuid is null
			  and (a.student is null or a.student.id <> :studentId)
			""")
	long countReservedSeatsExcludingStudent(
			@Param("year") AcademicYear year,
			@Param("grade") Grade grade,
			@Param("studentId") UUID studentId);

	Optional<AdmissionApplication> findByOfferTokenHash(String offerTokenHash);

	@Query("""
			select a from AdmissionApplication a
			join fetch a.academicYear
			join fetch a.grade
			where a.status = com.edushift.modules.admissions.entity.AdmissionApplicationStatus.ADMITTED
			  and a.offerStatus = com.edushift.modules.admissions.entity.AdmissionOfferStatus.PENDING
			  and a.offerSentAt <= :cutoff
			  and a.offerReminderSentAt is null
			""")
	List<AdmissionApplication> findOffersDueForReminder(@Param("cutoff") Instant cutoff);
}
