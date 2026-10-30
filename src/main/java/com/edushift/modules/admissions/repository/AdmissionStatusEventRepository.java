package com.edushift.modules.admissions.repository;

import com.edushift.modules.admissions.entity.AdmissionApplication;
import com.edushift.modules.admissions.entity.AdmissionStatusEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdmissionStatusEventRepository extends JpaRepository<AdmissionStatusEvent, UUID> {

	List<AdmissionStatusEvent> findAllByApplicationOrderByCreatedAtAsc(AdmissionApplication application);
}
