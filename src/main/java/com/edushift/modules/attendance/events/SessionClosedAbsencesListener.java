package com.edushift.modules.attendance.events;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.event.NotificationEvent;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT fan-out of {@code STUDENT_ABSENT} notifications for
 * materialized absences. Keeps {@code closeSession} transactions short.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionClosedAbsencesListener {

	private final StudentGuardianRepository studentGuardianRepository;
	private final UserRepository userRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionClosedAbsences(SessionClosedAbsencesEvent event) {
		if (event == null || event.absents() == null || event.absents().isEmpty()) {
			return;
		}
		if (event.tenantId() == null) {
			log.warn("[attendance] SessionClosedAbsencesEvent missing tenantId — skip fan-out session={}",
					event.sessionPublicUuid());
			return;
		}

		TenantContext.runAs(event.tenantId(), () -> {
			Map<UUID, SessionClosedAbsencesEvent.AbsentStudent> byStudentId = event.absents().stream()
					.filter(a -> a.studentId() != null)
					.collect(Collectors.toMap(
							SessionClosedAbsencesEvent.AbsentStudent::studentId,
							Function.identity(),
							(a, b) -> a));

			List<UUID> studentIds = List.copyOf(byStudentId.keySet());
			List<StudentGuardian> guardians =
					studentGuardianRepository.findActiveByStudentIdsWithLinkedGuardian(studentIds);

			Set<UUID> guardianUserInternalIds = new HashSet<>();
			for (StudentGuardian sg : guardians) {
				if (sg.getGuardian() != null && sg.getGuardian().getUserId() != null) {
					guardianUserInternalIds.add(sg.getGuardian().getUserId());
				}
			}
			Map<UUID, UUID> guardianInternalToPublic = new HashMap<>();
			for (User u : userRepository.findAllById(guardianUserInternalIds)) {
				guardianInternalToPublic.put(u.getId(), u.getPublicUuid());
			}

			int published = 0;
			int skipped = 0;
			String sectionName = event.sectionName() == null ? "" : event.sectionName();
			String dateStr = event.absentAt() == null ? "" : event.absentAt().toString();

			for (StudentGuardian sg : guardians) {
				Student student = sg.getStudent();
				if (student == null || student.getId() == null) {
					skipped++;
					continue;
				}
				Guardian guardian = sg.getGuardian();
				if (guardian == null || guardian.getUserId() == null) {
					skipped++;
					continue;
				}
				UUID guardianPublicUserId = guardianInternalToPublic.get(guardian.getUserId());
				if (guardianPublicUserId == null) {
					log.warn(
							"[attendance] absence fan-out -- skip; guardian user_id={} not found in users",
							guardian.getUserId());
					skipped++;
					continue;
				}
				SessionClosedAbsencesEvent.AbsentStudent absent = byStudentId.get(student.getId());
				UUID recordPublicUuid = absent == null ? null : absent.recordPublicUuid();
				String studentName = absent != null && absent.studentFullName() != null
						? absent.studentFullName()
						: student.fullName();
				UUID studentPublicUuid = absent != null && absent.studentPublicUuid() != null
						? absent.studentPublicUuid()
						: student.getPublicUuid();

				Map<String, Object> payload = new HashMap<>();
				payload.put("studentName", studentName);
				payload.put("date", dateStr);
				payload.put("reason", "");
				payload.put("courseName", sectionName);
				payload.put("parentName", guardian.fullName());
				if (studentPublicUuid != null) {
					payload.put("studentPublicUuid", studentPublicUuid.toString());
				}
				if (recordPublicUuid != null) {
					payload.put("recordPublicUuid", recordPublicUuid.toString());
				}

				eventPublisher.publishEvent(
						NotificationEvent.builder()
								.templateKey("STUDENT_ABSENT")
								.category(Notification.Category.ABSENCE)
								.sourceId(recordPublicUuid != null
										? recordPublicUuid
										: event.sessionPublicUuid())
								.tenantId(event.tenantId())
								.recipients(List.of(
										new NotificationEvent.Recipient(
												guardianPublicUserId,
												guardian.getEmail())))
								.payload(payload)
								.build());
				published++;
			}

			log.info(
					"[attendance] absence fan-out (after-commit) -- session={} materialized={} published={} skipped={}",
					event.sessionPublicUuid(),
					event.absents().size(),
					published,
					skipped);
			return Boolean.TRUE;
		});
	}
}
