package com.edushift.modules.teachers.listener;

import com.edushift.modules.teachers.assignments.event.TeacherAssignmentUnassignedEvent;
import com.edushift.modules.teachers.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 5 / DEBT-TEA-3 — mirrors
 * {@link TeacherAssignmentWorkloadListener} for the unassign direction.
 *
 * <p>Runs as a synchronous {@code @EventListener} so the counter bump
 * joins the same Hibernate session as the soft-end UPDATE on
 * {@code teacher_assignments}. If the outer transaction rolls back, the
 * counter decrement is undone too.</p>
 *
 * <p>The atomic JPQL UPDATE inside
 * {@link TeacherRepository#decrementAssignmentsCountByPublicUuid(UUID)}
 * carries a {@code t.assignmentsCount > 0} guard to defend against
 * duplicate / reordered events that would otherwise drive the counter
 * negative.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeacherAssignmentUnassignedWorkloadListener {

	private final TeacherRepository teacherRepository;

	@EventListener
	@Transactional
	public void onAssignmentUnassigned(TeacherAssignmentUnassignedEvent event) {
		int updated = teacherRepository.decrementAssignmentsCountByPublicUuid(
				event.teacherPublicUuid());
		if (updated == 0) {
			// Defensive: cross-tenant race OR counter already at 0 (event
			// reordering, duplicate event, pre-existing drift). Surface a
			// warning instead of throwing — the workload counter is a
			// cache, not the source of truth; the source of truth is the
			// teacher_assignments table itself.
			log.warn("[teachers.workload] assignment={} could not decrement counter for "
					+ "teacher={} (teacher not found, tenant scope mismatch, or counter at 0)",
					event.assignmentPublicUuid(), event.teacherPublicUuid());
			return;
		}
		log.info("[teachers.workload] teacher={} assignments_count decremented (assignment={})",
				event.teacherPublicUuid(), event.assignmentPublicUuid());
	}
}
