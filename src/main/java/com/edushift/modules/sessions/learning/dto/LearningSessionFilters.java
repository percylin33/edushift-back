package com.edushift.modules.sessions.learning.dto;

import com.edushift.modules.sessions.learning.entity.SessionStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Query parameters of {@code GET /v1/learning-sessions} (Sprint 5A /
 * BE-5A.4).
 *
 * <p>All filters are AND-combined. {@code dateFrom} / {@code dateTo}
 * are inclusive. {@code null} on every field returns the full active
 * tenant scope (capped by the controller-level page size).</p>
 *
 * <p>Resolution rules:</p>
 * <ul>
 *   <li>{@code teacherUuid} narrows to assignments whose teacher
 *       matches.</li>
 *   <li>{@code sectionUuid} narrows to assignments whose section
 *       matches.</li>
 *   <li>{@code unitUuid} narrows to sessions of that exact unit.</li>
 *   <li>{@code periodUuid} narrows to assignments whose period
 *       matches.</li>
 *   <li>{@code status} narrows to sessions in the specified state.</li>
 *   <li>{@code dateFrom} / {@code dateTo} bracket the
 *       {@code scheduled_date}.</li>
 * </ul>
 */
public record LearningSessionFilters(

		UUID teacherUuid,
		UUID sectionUuid,
		UUID unitUuid,
		UUID periodUuid,
		SessionStatus status,
		LocalDate dateFrom,
		LocalDate dateTo
) {

	/**
	 * @return true when every field is null (no filtering).
	 */
	public boolean isEmpty() {
		return teacherUuid == null
				&& sectionUuid == null
				&& unitUuid == null
				&& periodUuid == null
				&& status == null
				&& dateFrom == null
				&& dateTo == null;
	}
}
