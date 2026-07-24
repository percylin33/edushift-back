package com.edushift.modules.schedule.timeslot.service;

import com.edushift.shared.exception.BusinessException;
import java.util.UUID;

/**
 * Structured 409 returned when {@link ScheduleConflictDetector} finds
 * an overlap (Sprint cierre-C / B4).
 *
 * <p>Clients (FE) inspect {@link #getDimension()} + {@link #getConflictingEntityUuid()}
 * to highlight the offending cell in the schedule grid.</p>
 */
public class ScheduleConflictException extends BusinessException {

	public enum Dimension {
		TEACHER, CLASSROOM, SECTION
	}

	private final Dimension dimension;
	private final UUID conflictingEntityUuid;
	private final UUID conflictingSlotUuid;

	public ScheduleConflictException(Dimension dimension, UUID conflictingEntityUuid,
	                                 UUID conflictingSlotUuid, String message) {
		super("SCHEDULE_CONFLICT", message);
		this.dimension = dimension;
		this.conflictingEntityUuid = conflictingEntityUuid;
		this.conflictingSlotUuid = conflictingSlotUuid;
	}

	public Dimension getDimension() { return dimension; }
	public UUID getConflictingEntityUuid() { return conflictingEntityUuid; }
	public UUID getConflictingSlotUuid() { return conflictingSlotUuid; }
}