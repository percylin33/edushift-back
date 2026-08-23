package com.edushift.modules.schedule.daytemplate.service;

import com.edushift.modules.schedule.daytemplate.dto.ScheduleSettingsDto;
import com.edushift.modules.schedule.daytemplate.dto.UpdateScheduleSettingsRequest;

public interface ScheduleSettingsService {

	ScheduleSettingsDto getSettings();

	ScheduleSettingsDto updateSettings(UpdateScheduleSettingsRequest request);
}
