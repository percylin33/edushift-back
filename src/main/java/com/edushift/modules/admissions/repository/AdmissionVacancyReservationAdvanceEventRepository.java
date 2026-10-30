package com.edushift.modules.admissions.repository;

import com.edushift.modules.admissions.entity.AdmissionVacancyReservationAdvance;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservationAdvanceEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdmissionVacancyReservationAdvanceEventRepository
		extends JpaRepository<AdmissionVacancyReservationAdvanceEvent, UUID> {
	List<AdmissionVacancyReservationAdvanceEvent> findAllByAdvanceOrderByCreatedAtAsc(
			AdmissionVacancyReservationAdvance advance);

	List<AdmissionVacancyReservationAdvanceEvent> findAllByAdvance_Reservation_PublicUuidOrderByCreatedAtAsc(
			UUID reservationPublicUuid);
}
