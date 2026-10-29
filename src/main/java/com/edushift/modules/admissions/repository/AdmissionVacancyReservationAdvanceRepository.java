package com.edushift.modules.admissions.repository;

import com.edushift.modules.admissions.entity.AdmissionVacancyReservation;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservationAdvance;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdmissionVacancyReservationAdvanceRepository
		extends JpaRepository<AdmissionVacancyReservationAdvance, UUID> {

	List<AdmissionVacancyReservationAdvance> findAllByReservationOrderByReceivedAtAsc(
			AdmissionVacancyReservation reservation);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select a from AdmissionVacancyReservationAdvance a
		where a.reservation.application.publicUuid = :applicationPublicUuid
		  and a.status in (com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.AVAILABLE,
		                   com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.PARTIALLY_APPLIED)
		order by a.receivedAt asc
		""")
	List<AdmissionVacancyReservationAdvance> findAvailableForApplicationForUpdate(
			@Param("applicationPublicUuid") UUID applicationPublicUuid);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AdmissionVacancyReservationAdvance a where a.publicUuid = :publicUuid")
	java.util.Optional<AdmissionVacancyReservationAdvance> findByPublicUuidForUpdate(
			@Param("publicUuid") UUID publicUuid);

	@Query("""
		select coalesce(sum(a.amountCents - a.appliedCents), 0)
		from AdmissionVacancyReservationAdvance a
		where a.reservation = :reservation
		  and a.status in (com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.AVAILABLE,
		                   com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.PARTIALLY_APPLIED)
		""")
	long sumAvailableByReservation(@Param("reservation") AdmissionVacancyReservation reservation);

	@Query("""
		select coalesce(sum(a.amountCents), 0)
		from AdmissionVacancyReservationAdvance a
		where a.reservation = :reservation
		""")
	long sumReceivedByReservation(@Param("reservation") AdmissionVacancyReservation reservation);
}
