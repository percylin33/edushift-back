package com.edushift.modules.admissions.repository;

import com.edushift.modules.admissions.entity.AdmissionVacancyReservation;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdmissionVacancyReservationEventRepository extends JpaRepository<AdmissionVacancyReservationEvent, UUID> {
	List<AdmissionVacancyReservationEvent> findAllByReservationOrderByCreatedAtAsc(AdmissionVacancyReservation reservation);
}
