package com.edushift.modules.schedule.daytemplate.service;

import com.edushift.modules.schedule.daytemplate.dto.ScheduleWarningItem;
import java.util.List;
import java.util.UUID;

public interface ScheduleValidationService {

	List<ScheduleWarningItem> listWarnings(UUID yearUuid);
}
