package com.edushift.modules.schedule.timeslot.service;

import com.edushift.modules.schedule.timeslot.dto.CreateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.dto.ScheduleSlotItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotListItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse;
import com.edushift.modules.schedule.timeslot.dto.UpdateTimeSlotRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code TimeSlot} aggregate (Sprint 5A — BE-5A.3).
 *
 * <h3>Filters for the reverse views</h3>
 * <ul>
 *   <li>{@code periodUuid} — when non-null, narrows to assignments of
 *       that single period; when null, returns slots across every
 *       active period.</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>assignment / slot / teacher / section / period publicUuid
 *       unknown for the tenant</td></tr>
 *   <tr><td>{@code TIME_SLOT_DATE_INVERTED}</td><td>400</td>
 *       <td>{@code endTime <= startTime} (create or post-merge update)</td></tr>
 *   <tr><td>{@code TIME_SLOT_OVERLAP}</td><td>409</td>
 *       <td>another slot of the same assignment + day shares at least
 *       one minute with the candidate range</td></tr>
 *   <tr><td>{@code ASSIGNMENT_NOT_ACTIVE}</td><td>409</td>
 *       <td>create / update on a soft-ended assignment</td></tr>
 * </table>
 */
public interface TimeSlotService {

	List<TimeSlotListItem> listSlotsOfAssignment(UUID assignmentUuid);

	TimeSlotResponse getSlot(UUID slotUuid);

	TimeSlotResponse createSlot(UUID assignmentUuid, CreateTimeSlotRequest request);

	TimeSlotResponse updateSlot(UUID slotUuid, UpdateTimeSlotRequest request);

	void deleteSlot(UUID slotUuid);

	List<ScheduleSlotItem> getTeacherSchedule(UUID teacherUuid, UUID periodUuid);

	List<ScheduleSlotItem> getSectionSchedule(UUID sectionUuid, UUID periodUuid);
}
