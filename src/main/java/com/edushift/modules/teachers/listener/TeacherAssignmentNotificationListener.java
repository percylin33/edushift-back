package com.edushift.modules.teachers.listener;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.teachers.assignments.event.TeacherAssignmentCreatedEvent;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 5 / DEBT-TEA-1 cascade — fans out two {@code NotificationEvent}s
 * after a teacher assignment is created:
 *
 * <ol>
 *   <li><b>TEACHER_ASSIGNED</b> — to the assigned teacher (only if the
 *       teacher has a linked user account + email).</li>
 *   <li><b>SECTION_NEW_TEACHER</b> — to every ACTIVE-enrolled student of
 *       the section (only those with a linked user account).</li>
 * </ol>
 *
 * <p>Runs as a synchronous {@code @EventListener} so the
 * {@link ApplicationEventPublisher#publishEvent(Object) publishEvent} call
 * has the same Hibernate session as the assignment insert. The existing
 * {@code NotificationEventListener} consumes our
 * {@code NotificationEvent}s with
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)}, so actual
 * email/outbox dispatch happens after commit and never rolls back the
 * source tx.</p>
 *
 * <p>Multi-tenant: the listeners load Teacher, Section and enrollments
 * via the existing tenant-scoped repositories; Hibernate's
 * {@code @TenantId} discriminator is already populated for the create
 * call so all reads stay inside the same tenant.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeacherAssignmentNotificationListener {

	private final TeacherRepository teacherRepository;
	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final UserRepository userRepository;
	private final ApplicationEventPublisher eventPublisher;

	@EventListener
	@Transactional(readOnly = true)
	public void onAssignmentCreated(TeacherAssignmentCreatedEvent event) {
		Teacher teacher = teacherRepository.findByPublicUuid(event.teacherPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Teacher", event.teacherPublicUuid()));

		Section section = sectionRepository.findByPublicUuid(event.sectionPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Section", event.sectionPublicUuid()));

		fanOutToTeacher(event, teacher, section);
		fanOutToStudents(event, teacher, section);
	}

	// ---------------------------------------------------------------------
	// Internal fan-out helpers
	// ---------------------------------------------------------------------

	private void fanOutToTeacher(TeacherAssignmentCreatedEvent event,
			Teacher teacher, Section section) {
		UUID teacherInternalUserId = teacher.getUserId();
		if (teacherInternalUserId == null) {
			log.info("[teachers.notifications] skip teacher-notify -- teacher={} has no linked user account",
					teacher.getPublicUuid());
			return;
		}
		// DEBT-NOTIF-4 fix (Sprint 9A): resolve internal id -> publicUuid
		// here so the listener async no longer needs the defensive
		// resolver and the FK to users(public_uuid) passes directly.
		java.util.Optional<com.edushift.modules.auth.entity.User> teacherUser =
				userRepository.findById(teacherInternalUserId);
		if (teacherUser.isEmpty()) {
			log.warn("[teachers.notifications] skip teacher-notify -- teacher={} has user_id={} but no users row (orphaned link)",
					teacher.getPublicUuid(), teacherInternalUserId);
			return;
		}
		UUID teacherPublicUserId = teacherUser.get().getPublicUuid();

		eventPublisher.publishEvent(
				com.edushift.modules.notifications.event.NotificationEvent.builder()
						.templateKey("TEACHER_ASSIGNED")
						.category(com.edushift.modules.notifications.entity.Notification.Category.SYSTEM)
						.sourceId(event.assignmentPublicUuid())
						.tenantId(event.tenantId())
						.recipients(List.of(
								new com.edushift.modules.notifications.event.NotificationEvent.Recipient(
										teacherPublicUserId, teacher.getEmail())))
						.payload(Map.of(
								"teacherName", teacher.fullName(),
								"courseCode", section.getGrade().getLevel().getCode(),
								"sectionName", section.getName(),
								"assignmentPublicUuid", event.assignmentPublicUuid().toString(),
								"teacherPublicUuid", teacher.getPublicUuid().toString()))
						.build());
	}

	private void fanOutToStudents(TeacherAssignmentCreatedEvent event,
			Teacher teacher, Section section) {
		List<StudentEnrollment> enrolled =
				studentEnrollmentRepository.findActiveBySection(section);
		if (enrolled.isEmpty()) {
			log.info("[teachers.notifications] skip student-fanout -- section={} has 0 active enrollments",
					section.getPublicUuid());
			return;
		}

		List<com.edushift.modules.notifications.event.NotificationEvent.Recipient> recipients =
				new ArrayList<>(enrolled.size());
		// DEBT-NOTIF-4 fix (Sprint 9A): build a single bulk lookup map
		// (internal id -> public uuid) so we don't N+1 the users table.
		java.util.Set<UUID> studentInternalIds = new java.util.HashSet<>();
		for (StudentEnrollment e : enrolled) {
			if (e.getStudent() != null && e.getStudent().getUserId() != null) {
				studentInternalIds.add(e.getStudent().getUserId());
			}
		}
		java.util.Map<UUID, UUID> internalToPublic = new java.util.HashMap<>();
		for (com.edushift.modules.auth.entity.User u : userRepository.findAllById(studentInternalIds)) {
			internalToPublic.put(u.getId(), u.getPublicUuid());
		}
		for (StudentEnrollment e : enrolled) {
			UUID internalId = e.getStudent() == null ? null : e.getStudent().getUserId();
			String email = e.getStudent() == null ? null : e.getStudent().getEmail();
			UUID publicId = internalId == null ? null : internalToPublic.get(internalId);
			if (publicId == null) {
				continue;
			}
			recipients.add(new com.edushift.modules.notifications.event.NotificationEvent.Recipient(
					publicId, email));
		}

		if (recipients.isEmpty()) {
			log.info("[teachers.notifications] skip student-fanout -- section={} has 0 enrolled students with user account",
					section.getPublicUuid());
			return;
		}

		eventPublisher.publishEvent(
				com.edushift.modules.notifications.event.NotificationEvent.builder()
						.templateKey("SECTION_NEW_TEACHER")
						.category(com.edushift.modules.notifications.entity.Notification.Category.ANNOUNCEMENT)
						.sourceId(event.assignmentPublicUuid())
						.tenantId(event.tenantId())
						.recipients(recipients)
						.payload(Map.of(
								"teacherName", teacher.fullName(),
								"sectionName", section.getName(),
								"levelCode", section.getGrade().getLevel().getCode(),
								"assignmentPublicUuid", event.assignmentPublicUuid().toString()))
						.build());

		log.info("[teachers.notifications] fanned out SECTION_NEW_TEACHER to {} students in section={}",
				recipients.size(), section.getPublicUuid());
	}
}
