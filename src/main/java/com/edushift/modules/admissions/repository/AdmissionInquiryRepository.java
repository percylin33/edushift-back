package com.edushift.modules.admissions.repository;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.admissions.entity.AdmissionInquiry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdmissionInquiryRepository extends JpaRepository<AdmissionInquiry, UUID> {
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select i from AdmissionInquiry i where i.publicUuid = :publicUuid")
	Optional<AdmissionInquiry> findByPublicUuidForUpdate(@org.springframework.data.repository.query.Param("publicUuid") UUID publicUuid);

	Optional<AdmissionInquiry> findByPublicUuid(UUID publicUuid);

	List<AdmissionInquiry> findAllByAcademicYearOrderByCreatedAtDesc(AcademicYear academicYear);
}
