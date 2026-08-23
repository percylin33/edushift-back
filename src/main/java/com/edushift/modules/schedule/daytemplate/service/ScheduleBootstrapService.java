package com.edushift.modules.schedule.daytemplate.service;

import com.edushift.modules.schedule.daytemplate.dto.CommitBootstrapRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.dto.ScheduleSourceDocumentResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ScheduleBootstrapService {

	ScheduleSourceDocumentResponse upload(UUID yearUuid, MultipartFile file);

	List<ScheduleSourceDocumentResponse> list(UUID yearUuid);

	/**
	 * Applies jornada draft rows if present; otherwise seeds default
	 * day templates for the year.
	 */
	List<DayTemplateResponse> commit(UUID yearUuid, UUID documentUuid, CommitBootstrapRequest request);
}
