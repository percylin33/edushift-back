package com.edushift.modules.admissions.repository;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

@Repository
public interface AdmissionVacancyReservationRepository extends JpaRepository<AdmissionVacancyReservation, UUID> {
	Optional<AdmissionVacancyReservation> findByApplication_Id(UUID applicationId);
	Optional<AdmissionVacancyReservation> findByPublicUuid(UUID publicUuid);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from AdmissionVacancyReservation r where r.publicUuid = :publicUuid")
	Optional<AdmissionVacancyReservation> findByPublicUuidForUpdate(@Param("publicUuid") UUID publicUuid);

	@Query("""
		select count(r) from AdmissionVacancyReservation r
		where r.academicYear = :year and r.grade = :grade
		  and r.status in (com.edushift.modules.admissions.entity.AdmissionReservationStatus.TEMPORARY,
		                   com.edushift.modules.admissions.entity.AdmissionReservationStatus.COMMITTED)
		  and (r.status = com.edushift.modules.admissions.entity.AdmissionReservationStatus.COMMITTED
		       or r.expiresAt > :now)
		""")
	long countEffective(@Param("year") AcademicYear year, @Param("grade") Grade grade, @Param("now") Instant now);

	@Query("""
		select count(r) from AdmissionVacancyReservation r
		where r.academicYear = :year and r.grade = :grade
		  and r.application.id <> :applicationId
		  and r.status in (com.edushift.modules.admissions.entity.AdmissionReservationStatus.TEMPORARY,
		                   com.edushift.modules.admissions.entity.AdmissionReservationStatus.COMMITTED)
		  and (r.status = com.edushift.modules.admissions.entity.AdmissionReservationStatus.COMMITTED
		       or r.expiresAt > :now)
		""")
	long countEffectiveExcludingApplication(@Param("year") AcademicYear year, @Param("grade") Grade grade,
			@Param("applicationId") UUID applicationId, @Param("now") Instant now);

	@Query("""
		select count(r) from AdmissionVacancyReservation r
		where r.academicYear = :year and r.grade = :grade
		  and (r.application.student is null or r.application.student.id <> :studentId)
		  and r.status in (com.edushift.modules.admissions.entity.AdmissionReservationStatus.TEMPORARY,
		                   com.edushift.modules.admissions.entity.AdmissionReservationStatus.COMMITTED)
		  and (r.status = com.edushift.modules.admissions.entity.AdmissionReservationStatus.COMMITTED
		       or r.expiresAt > :now)
		""")
	long countEffectiveExcludingStudent(@Param("year") AcademicYear year, @Param("grade") Grade grade,
			@Param("studentId") UUID studentId, @Param("now") Instant now);

	@Query("""
		select count(r) from AdmissionVacancyReservation r
		where r.academicYear = :year and r.grade = :grade
		  and r.status = com.edushift.modules.admissions.entity.AdmissionReservationStatus.TEMPORARY
		  and r.expiresAt > :now
		""")
	long countTemporary(@Param("year") AcademicYear year, @Param("grade") Grade grade, @Param("now") Instant now);

	@Query("""
		select r from AdmissionVacancyReservation r
		join fetch r.application
		join fetch r.academicYear
		join fetch r.grade
		where r.academicYear = :year
		order by r.createdAt desc
		""")
	List<AdmissionVacancyReservation> findAllByYear(@Param("year") AcademicYear year);

	@Query("""
		select r from AdmissionVacancyReservation r
		where r.status = com.edushift.modules.admissions.entity.AdmissionReservationStatus.TEMPORARY
		  and r.expiresAt <= :now
		""")
	List<AdmissionVacancyReservation> findExpired(@Param("now") Instant now);

	@Query("""
		select r from AdmissionVacancyReservation r
		where r.academicYear = :year
		  and r.status = com.edushift.modules.admissions.entity.AdmissionReservationStatus.TEMPORARY
		  and r.expiresAt <= :now
		""")
	List<AdmissionVacancyReservation> findExpiredByYear(@Param("year") AcademicYear year,
			@Param("now") Instant now);
}
