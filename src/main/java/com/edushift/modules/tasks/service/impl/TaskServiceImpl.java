package com.edushift.modules.tasks.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.event.NotificationEvent;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.tasks.dto.CreateTaskRequest;
import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.TaskSummary;
import com.edushift.modules.tasks.dto.UpdateTaskRequest;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.entity.TaskStatus;
import com.edushift.modules.tasks.exception.DueAtInPastException;
import com.edushift.modules.tasks.exception.InvalidTaskStateException;
import com.edushift.modules.tasks.exception.RecordEmptyPatchException;
import com.edushift.modules.tasks.exception.SectionNotFoundException;
import com.edushift.modules.tasks.exception.TaskNotFoundException;
import com.edushift.modules.tasks.mapper.TaskMapper;
import com.edushift.modules.tasks.repository.TaskRepository;
import com.edushift.modules.tasks.service.TaskService;
import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import com.edushift.modules.tasks.submission.repository.SubmissionRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.support.LmsAssignmentBinder;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link TaskService} implementation (Sprint 7a / BE-7a.2).
 *
 * <h3>Multi-tenant safety (audit §6)</h3>
 * All reads & writes go through {@code @TenantId}-filtered
 * repository methods; cross-tenant lookups resolve as 404
 * (anti-enumeration).
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   DRAFT ──publish──▶ PUBLISHED ──archive──▶ ARCHIVED
 *     │                     │
 *     │                     └── soft-delete (admin only)
 *     └── soft-delete (owner / admin)
 * </pre>
 *
 * <p>Lifecycle methods added as part of BUG-2026-07-31-04.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

	private final TaskRepository taskRepository;
	private final SectionRepository sectionRepository;
	private final FileObjectService fileObjectService;
	private final TaskMapper taskMapper;
	private final LmsAssignmentBinder lmsAssignmentBinder;
	private final ApplicationEventPublisher eventPublisher;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final UserRepository userRepository;
	private final SubmissionRepository submissionRepository;

	@Override
	@Transactional
	public TaskResponse create(UUID sectionPublicUuid,
			CreateTaskRequest request, UUID ownerUserId) {
		Section section = requireSection(sectionPublicUuid);
		validateDueAt(request.dueAt());
		TeacherAssignment assignment = lmsAssignmentBinder.requireForCreate(
				request.teacherAssignmentPublicUuid(), section);

		UUID attachmentPublicUuid = request.attachmentPublicUuid();
		if (attachmentPublicUuid != null) {
			Optional<FileObject> file = fileObjectService.findByPublicUuid(
					attachmentPublicUuid);
			if (file.isEmpty()) {
				throw new com.edushift.shared.exception.BadRequestException(
						"FILE_NOT_FOUND",
						"attachmentPublicUuid not found in tenant: "
								+ attachmentPublicUuid);
			}
		}

		Task entity = new Task();
		entity.setSection(section);
		entity.setTeacherAssignment(assignment);
		entity.setTitle(request.title());
		entity.setDescription(request.description());
		entity.setDueAt(request.dueAt());
		entity.setAttachmentPublicUuid(attachmentPublicUuid);
		entity.setOwnerUserId(ownerUserId);
		entity.setMaxScore(request.maxScore().shortValue());
		entity.setAllowResubmission(
				request.allowResubmission() == null || request.allowResubmission());
		Task saved = taskRepository.save(entity);

		if (attachmentPublicUuid != null) {
			fileObjectService.acquireReference(attachmentPublicUuid);
		}

		return taskMapper.toResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<TaskSummary> listBySection(UUID sectionPublicUuid, Pageable pageable) {
		return listBySection(sectionPublicUuid, null, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<TaskSummary> listBySection(
			UUID sectionPublicUuid, UUID assignmentPublicUuid, Pageable pageable) {
		Optional<Section> section = sectionRepository.findByPublicUuid(sectionPublicUuid);
		if (section.isEmpty()) {
			return Page.empty(pageable);
		}
		if (assignmentPublicUuid != null) {
			TeacherAssignment assignment = lmsAssignmentBinder.requireForFilter(
					assignmentPublicUuid, section.get());
			return enrichSummaries(taskRepository
					.findAllBySectionAndTeacherAssignmentOrderByDueAtDesc(
							section.get(), assignment, pageable));
		}
		return enrichSummaries(taskRepository
				.findAllBySectionOrderByDueAtDesc(section.get(), pageable));
	}

	@Override
	@Transactional(readOnly = true)
	public TaskResponse getByPublicUuid(UUID publicUuid) {
		Task task = requireTask(publicUuid);
		int total = (int) submissionRepository.countByTask(task);
		int graded = (int) submissionRepository.countByTaskAndStatus(task, SubmissionStatus.GRADED);
		return taskMapper.toResponse(task, total, graded);
	}

	@Override
	@Transactional
	public TaskResponse patch(UUID publicUuid, UpdateTaskRequest request) {
		if (isAllNull(request)) {
			throw new RecordEmptyPatchException();
		}
		Task entity = requireTask(publicUuid);

		// Lifecycle guard: PATCH is only allowed in DRAFT. Once
		// PUBLISHED, the task is "frozen" for students and only
		// archive is acceptable. (Same pattern as QuizServiceImpl.)
		requireDraft(entity);

		if (request.title() != null) {
			entity.setTitle(request.title());
		}
		if (request.description() != null) {
			entity.setDescription(request.description());
		}
		if (request.dueAt() != null) {
			validateDueAt(request.dueAt());
			entity.setDueAt(request.dueAt());
		}
		if (request.attachmentPublicUuid() != null
				&& !request.attachmentPublicUuid().equals(entity.getAttachmentPublicUuid())) {
			// Replace attachment: release the old, acquire the new.
			UUID oldAttachment = entity.getAttachmentPublicUuid();
			UUID newAttachment = request.attachmentPublicUuid();
			Optional<FileObject> file = fileObjectService.findByPublicUuid(newAttachment);
			if (file.isEmpty()) {
				throw new com.edushift.shared.exception.BadRequestException(
						"FILE_NOT_FOUND",
						"attachmentPublicUuid not found in tenant: " + newAttachment);
			}
			entity.setAttachmentPublicUuid(newAttachment);
			if (oldAttachment != null) {
				fileObjectService.releaseReference(oldAttachment);
			}
			fileObjectService.acquireReference(newAttachment);
		}
		if (request.allowResubmission() != null) {
			entity.setAllowResubmission(request.allowResubmission());
		}
		if (request.maxScore() != null) {
			entity.setMaxScore(request.maxScore().shortValue());
		}

		Task saved = taskRepository.save(entity);
		return taskMapper.toResponse(saved);
	}

	@Override
	@Transactional
	public TaskResponse publish(UUID publicUuid) {
		Task entity = requireTask(publicUuid);
		if (entity.getStatus() != TaskStatus.DRAFT) {
			throw InvalidTaskStateException.notDraft(
					entity.getStatus() == null ? "null" : entity.getStatus().name());
		}
		entity.setStatus(TaskStatus.PUBLISHED);
		entity.setPublishedAt(Instant.now());
		Task saved = taskRepository.save(entity);
		log.info("Task published publicUuid={}", publicUuid);
		notifyTaskPublished(saved);
		return taskMapper.toResponse(saved);
	}

	@Override
	@Transactional
	public TaskResponse archive(UUID publicUuid) {
		Task entity = requireTask(publicUuid);
		if (entity.getStatus() == TaskStatus.ARCHIVED) {
			throw InvalidTaskStateException.alreadyArchived();
		}
		if (entity.getStatus() == TaskStatus.DRAFT) {
			throw InvalidTaskStateException.notPublished(
					entity.getStatus() == null ? "null" : entity.getStatus().name());
		}
		entity.setStatus(TaskStatus.ARCHIVED);
		entity.setArchivedAt(Instant.now());
		Task saved = taskRepository.save(entity);
		log.info("Task archived publicUuid={}", publicUuid);
		return taskMapper.toResponse(saved);
	}

	@Override
	@Transactional
	public void delete(UUID publicUuid) {
		Task entity = requireTask(publicUuid);
		UUID attachmentPublicUuid = entity.getAttachmentPublicUuid();
		taskRepository.delete(entity);
		if (attachmentPublicUuid != null) {
			try {
				fileObjectService.releaseReference(attachmentPublicUuid);
			}
			catch (RuntimeException ex) {
				log.warn("releaseReference failed for task {} file {}: {}",
						publicUuid, attachmentPublicUuid, ex.getMessage());
			}
		}
		// (D-TSK-05) submissions are NOT removed; they remain
		// orphaned. Future housekeeping (DEBT-7A-18) handles
		// archival.
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	/**
	 * Fan-out {@code TASK_PUBLISHED} to every actively enrolled student
	 * in the task's section (same pattern as {@code QUIZ_PUBLISHED}).
	 */
	private void notifyTaskPublished(Task saved) {
		Section section = saved.getSection();
		if (section == null) {
			return;
		}
		List<StudentEnrollment> enrolled = studentEnrollmentRepository.findActiveBySection(section);
		if (enrolled.isEmpty()) {
			return;
		}
		String dueDate = saved.getDueAt() == null ? "" : saved.getDueAt().toString();
		String courseName = section.getName() == null ? "" : section.getName();

		// DEBT-NOTIF-4: resolve internal user ids → public UUIDs so the
		// async listener receives recipient.userId matching users(public_uuid).
		Set<UUID> studentUserInternalIds = new HashSet<>();
		for (StudentEnrollment e : enrolled) {
			UUID uid = e.getStudent() == null ? null : e.getStudent().getUserId();
			if (uid != null) {
				studentUserInternalIds.add(uid);
			}
		}
		if (studentUserInternalIds.isEmpty()) {
			return;
		}
		Map<UUID, UUID> linkedToPublic = new HashMap<>();
		Map<UUID, String> publicToEmail = new HashMap<>();
		for (com.edushift.modules.auth.entity.User u : userRepository.findAllById(studentUserInternalIds)) {
			linkedToPublic.put(u.getId(), u.getPublicUuid());
			if (u.getEmail() != null && !u.getEmail().isBlank()) {
				publicToEmail.put(u.getPublicUuid(), u.getEmail());
			}
		}
		for (UUID linkedId : studentUserInternalIds) {
			if (!linkedToPublic.containsKey(linkedId)) {
				userRepository.findByPublicUuid(linkedId).ifPresent(u -> {
					linkedToPublic.put(linkedId, u.getPublicUuid());
					if (u.getEmail() != null && !u.getEmail().isBlank()) {
						publicToEmail.put(u.getPublicUuid(), u.getEmail());
					}
				});
			}
		}

		List<NotificationEvent.Recipient> recipients = enrolled.stream()
				.map(e -> {
					UUID linkedId = e.getStudent() == null ? null : e.getStudent().getUserId();
					if (linkedId == null) {
						return null;
					}
					UUID publicId = linkedToPublic.get(linkedId);
					if (publicId == null) {
						return null;
					}
					String email = publicToEmail.get(publicId);
					if ((email == null || email.isBlank()) && e.getStudent() != null) {
						email = e.getStudent().getEmail();
					}
					return new NotificationEvent.Recipient(publicId, email);
				})
				.filter(Objects::nonNull)
				.toList();

		Map<String, Object> payload = new HashMap<>();
		payload.put("studentName", "");
		payload.put("taskTitle", saved.getTitle() == null ? "" : saved.getTitle());
		payload.put("taskPublicUuid", saved.getPublicUuid() == null ? "" : saved.getPublicUuid().toString());
		payload.put("courseName", courseName);
		payload.put("dueDate", dueDate);

		recipients.forEach(r -> eventPublisher.publishEvent(
				NotificationEvent.builder()
						.templateKey("TASK_PUBLISHED")
						.category(Notification.Category.TASK)
						.sourceId(saved.getPublicUuid())
						.tenantId(TenantContext.currentRequired())
						.recipients(List.of(r))
						.payload(payload)
						.build()));
	}

	private Page<TaskSummary> enrichSummaries(Page<Task> page) {
		List<Task> tasks = page.getContent();
		if (tasks.isEmpty()) {
			return page.map(t -> taskMapper.toSummary(t, 0, 0));
		}
		List<UUID> taskIds = tasks.stream().map(Task::getId).toList();
		Map<UUID, Integer> totalCounts = new HashMap<>();
		for (Object[] row : submissionRepository.countGroupedByTaskId(taskIds)) {
			totalCounts.put((UUID) row[0], ((Number) row[1]).intValue());
		}
		Map<UUID, Integer> gradedCounts = new HashMap<>();
		for (Object[] row : submissionRepository.countGradedGroupedByTaskId(taskIds)) {
			gradedCounts.put((UUID) row[0], ((Number) row[1]).intValue());
		}
		return page.map(t -> taskMapper.toSummary(
				t,
				totalCounts.getOrDefault(t.getId(), 0),
				gradedCounts.getOrDefault(t.getId(), 0)));
	}

	private Task requireTask(UUID publicUuid) {
		return taskRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new TaskNotFoundException(publicUuid.toString()));
	}

	private Section requireSection(UUID sectionPublicUuid) {
		return sectionRepository.findByPublicUuid(sectionPublicUuid)
				.orElseThrow(() -> new SectionNotFoundException(sectionPublicUuid.toString()));
	}

	private static void requireDraft(Task task) {
		if (task.getStatus() != TaskStatus.DRAFT) {
			throw InvalidTaskStateException.notDraft(
					task.getStatus() == null ? "null" : task.getStatus().name());
		}
	}

	private static void validateDueAt(Instant dueAt) {
		if (dueAt != null && dueAt.isBefore(Instant.now())) {
			throw new DueAtInPastException();
		}
	}

	private static boolean isAllNull(UpdateTaskRequest r) {
		return r.title() == null
				&& r.description() == null
				&& r.dueAt() == null
				&& r.attachmentPublicUuid() == null
				&& r.allowResubmission() == null
				&& r.maxScore() == null;
	}
}
