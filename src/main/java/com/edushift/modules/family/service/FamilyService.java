package com.edushift.modules.family.service;

import com.edushift.modules.family.dto.FamilyActivityItemDto;
import com.edushift.modules.family.dto.FamilyAttendanceRecordDto;
import com.edushift.modules.family.dto.FamilyChildSummary;
import com.edushift.modules.family.dto.FamilyGradeItemDto;
import com.edushift.modules.family.dto.FamilyPaymentItemDto;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FamilyService {
	List<FamilyChildSummary> listChildren(UUID parentUserPublicUuid);

	Page<FamilyAttendanceRecordDto> getChildAttendance(
			UUID studentPublicUuid, UUID parentUserPublicUuid, Pageable pageable);

	List<FamilyGradeItemDto> getChildGrades(UUID studentPublicUuid, UUID parentUserPublicUuid);

	List<FamilyActivityItemDto> getChildActivities(UUID studentPublicUuid, UUID parentUserPublicUuid);

	List<FamilyPaymentItemDto> getChildPayments(UUID studentPublicUuid, UUID parentUserPublicUuid);

	ScheduleWeekView getChildSchedule(
			UUID studentPublicUuid, UUID parentUserPublicUuid, UUID periodUuid);
}
