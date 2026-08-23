package com.edushift.modules.schedule.daytemplate.mapper;

import com.edushift.modules.schedule.daytemplate.dto.ScheduleSourceDocumentResponse;
import com.edushift.modules.schedule.daytemplate.entity.ScheduleSourceDocument;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ScheduleSourceDocumentMapper {

	public ScheduleSourceDocumentResponse toResponse(ScheduleSourceDocument doc) {
		Map<String, Object> draft = doc.getParsedDraftJson();
		return new ScheduleSourceDocumentResponse(
				doc.getPublicUuid(),
				doc.getAcademicYear() != null ? doc.getAcademicYear().getPublicUuid() : null,
				doc.getKind(),
				doc.getParseStatus(),
				doc.getOriginalFilename(),
				doc.getContentType(),
				doc.getFileSizeBytes() == null ? 0L : doc.getFileSizeBytes(),
				draft == null ? Map.of() : Map.copyOf(draft),
				doc.getParseError(),
				doc.getCreatedAt()
		);
	}
}
