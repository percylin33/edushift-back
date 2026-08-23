package com.edushift.modules.schedule.daytemplate.service;

import com.edushift.modules.schedule.daytemplate.dto.CloneScheduleRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import java.util.List;
import java.util.UUID;

public interface ScheduleCloneService {

	List<DayTemplateResponse> cloneSchedule(UUID targetYearUuid, CloneScheduleRequest request);
}
