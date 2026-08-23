package com.edushift.modules.schedule.daytemplate.mapper;

import com.edushift.modules.schedule.daytemplate.dto.CreateDayBlockRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayBlockResponse;
import com.edushift.modules.schedule.daytemplate.dto.UpdateDayBlockRequest;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleBlock;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import org.springframework.stereotype.Component;

@Component
public class DayScheduleBlockMapper {

	public DayBlockResponse toResponse(DayScheduleBlock block) {
		return new DayBlockResponse(
				block.getPublicUuid(),
				block.getDayOfWeek(),
				block.getStartTime(),
				block.getEndTime(),
				block.getBlockType(),
				block.getLabel()
		);
	}

	public DayScheduleBlock fromCreate(CreateDayBlockRequest request, DayScheduleTemplate template) {
		DayScheduleBlock block = new DayScheduleBlock();
		block.setTemplate(template);
		block.setDayOfWeek(request.dayOfWeek());
		block.setStartTime(request.startTime());
		block.setEndTime(request.endTime());
		block.setBlockType(request.blockType());
		block.setLabel(request.label());
		return block;
	}

	public void applyUpdate(UpdateDayBlockRequest patch, DayScheduleBlock block) {
		if (patch == null) {
			return;
		}
		if (patch.dayOfWeek() != null) {
			block.setDayOfWeek(patch.dayOfWeek());
		}
		if (patch.startTime() != null) {
			block.setStartTime(patch.startTime());
		}
		if (patch.endTime() != null) {
			block.setEndTime(patch.endTime());
		}
		if (patch.blockType() != null) {
			block.setBlockType(patch.blockType());
		}
		if (patch.label() != null) {
			block.setLabel(patch.label().isBlank() ? block.getLabel() : patch.label().trim());
		}
	}
}
