package com.edushift.modules.schedule.daytemplate.service;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.schedule.daytemplate.dto.NonTeachingBlockItem;
import com.edushift.modules.schedule.timeslot.service.ScheduleConflictException;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves hard non-teaching windows for a section and asserts that a
 * candidate slot does not overlap recess / lunch / assembly.
 *
 * <p>Intended for {@code ScheduleConflictDetector} and session
 * materialization (ADR-SCH-6 / ADR-SCH-9).</p>
 */
@Component
@RequiredArgsConstructor
public class NonTeachingBlockResolver {

	private final DayScheduleTemplateService dayScheduleTemplateService;

	public void assertNoOverlapWithRecess(Section section, Short day,
			LocalTime start, LocalTime end) {
		if (section == null || start == null || end == null) {
			return;
		}
		List<NonTeachingBlockItem> blocks = dayScheduleTemplateService
				.listHardNonTeachingBlocksForSection(section, day);
		for (NonTeachingBlockItem block : blocks) {
			if (overlaps(block.startTime(), block.endTime(), start, end)) {
				throw new ScheduleConflictException(
						ScheduleConflictException.Dimension.SECTION,
						section.getPublicUuid(),
						block.blockPublicUuid(),
						String.format(
								"El horario se solapa con un bloque no lectivo (%s: %s–%s).",
								block.label() != null ? block.label() : block.blockType(),
								block.startTime(),
								block.endTime()));
			}
		}
	}

	private static boolean overlaps(LocalTime aStart, LocalTime aEnd,
			LocalTime bStart, LocalTime bEnd) {
		return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
	}
}
