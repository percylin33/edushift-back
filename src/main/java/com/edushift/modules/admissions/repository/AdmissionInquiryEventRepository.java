package com.edushift.modules.admissions.repository;

import com.edushift.modules.admissions.entity.AdmissionInquiry;
import com.edushift.modules.admissions.entity.AdmissionInquiryEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdmissionInquiryEventRepository extends JpaRepository<AdmissionInquiryEvent, UUID> {
	List<AdmissionInquiryEvent> findAllByInquiryOrderByCreatedAtAsc(AdmissionInquiry inquiry);
}
