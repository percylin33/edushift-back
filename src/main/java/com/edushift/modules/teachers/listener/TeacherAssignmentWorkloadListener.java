package com.edushift.modules.teachers.listener;

import com.edushift.modules.teachers.assignments.event.TeacherAssignmentCreatedEvent;
import com.edushift.modules.teachers.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 5 / DEBT-TEA-1 cascade — bumps the teacher's
 * {@code assignments_count} when a new assignment is created (and decrements
 * it on the matching unassign flow).
 *
 * <p>Runs as a synchronous {@code @EventListener} so the counter stays
 * consistent with the {@code teacher_assignments} row: if the outer
 * transaction rolls back, the counter bump is undone as well.</p>
 *
 * <p>The increment uses a single atomic JPQL UPDATE so we bypass the
 * read-modify-write race that {@code teacher.assignmentsCount++} on a
 * JPA-managed entity would have under concurrent creates.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeacherAssignmentWorkloadListener {

	private final TeacherRepository teacherRepository;

	@EventListener
	@Transactional
	public void onAssignmentCreated(TeacherAssignmentCreatedEvent event) {
		int updated = teacherRepository.incrementAssignmentsCountByPublicUuid(
				event.teacherPublicUuid());
		if (updated == 0) {
			// Defensive: cross-tenant races (race-after-delete) — leave a
			// breadcrumb instead of failing the source tx.
			log.warn("[teachers.workload] assignment={} could not bump counter for teacher={} "
					+ "(teacher not found or tenant scope mismatch)",
					event.assignmentPublicUuid(), event.teacherPublicUuid());
			return;
		}
		log.info("[teachers.workload] teacher={} assignments_count incremented (assignment={})",
				event.teacherPublicUuid(), event.assignmentPublicUuid());
	}
}
