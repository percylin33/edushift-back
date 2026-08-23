package com.edushift.modules.schedule.daytemplate.repository;

import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.schedule.daytemplate.entity.ScheduleSourceDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleSourceDocumentRepository extends JpaRepository<ScheduleSourceDocument, UUID> {

	Optional<ScheduleSourceDocument> findByPublicUuid(UUID publicUuid);

	List<ScheduleSourceDocument> findByAcademicYear_PublicUuidOrderByCreatedAtDesc(UUID yearPublicUuid);

	List<ScheduleSourceDocument> findAllByAcademicYearOrderByCreatedAtDesc(AcademicYear year);
}
