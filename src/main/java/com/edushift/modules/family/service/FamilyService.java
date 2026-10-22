package com.edushift.modules.family.service;

import com.edushift.modules.family.dto.FamilyActivityItemDto;
import com.edushift.modules.family.dto.FamilyAttendanceRecordDto;
import com.edushift.modules.family.dto.FamilyChildSummary;
import com.edushift.modules.family.dto.FamilyCompetencyResultDto;
import com.edushift.modules.family.dto.FamilyGradeItemDto;
import com.edushift.modules.family.dto.FamilyPaymentItemDto;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.modules.reports.entity.ReportJob;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FamilyService {
	List<FamilyChildSummary> listChildren(UUID parentUserPublicUuid);

	Page<FamilyAttendanceRecordDto> getChildAttendance(
			UUID studentPublicUuid,
			UUID parentUserPublicUuid,
			LocalDate from,
			LocalDate to,
			Pageable pageable);

	byte[] exportChildAttendance(
			UUID studentPublicUuid,
			UUID parentUserPublicUuid,
			LocalDate from,
			LocalDate to,
			ReportJob.Format format) throws java.io.IOException;

	List<FamilyGradeItemDto> getChildGrades(UUID studentPublicUuid, UUID parentUserPublicUuid);

	List<FamilyCompetencyResultDto> getChildCompetencyResults(
			UUID studentPublicUuid, UUID parentUserPublicUuid);

	List<FamilyActivityItemDto> getChildActivities(UUID studentPublicUuid, UUID parentUserPublicUuid);

	List<FamilyPaymentItemDto> getChildPayments(UUID studentPublicUuid, UUID parentUserPublicUuid);

	ScheduleWeekView getChildSchedule(
			UUID studentPublicUuid, UUID parentUserPublicUuid, UUID periodUuid);
}
