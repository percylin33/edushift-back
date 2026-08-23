package com.edushift.modules.schedule.daytemplate.service;

import com.edushift.modules.schedule.daytemplate.dto.TeacherWorkloadItem;
import java.util.List;
import java.util.UUID;

public interface TeacherWorkloadService {

	List<TeacherWorkloadItem> listWorkload(UUID periodUuid);

	TeacherWorkloadItem getWorkload(UUID teacherUuid, UUID periodUuid);
}
