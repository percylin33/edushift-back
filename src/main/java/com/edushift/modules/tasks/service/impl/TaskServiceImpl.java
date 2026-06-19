package com.edushift.modules.tasks.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.tasks.dto.CreateTaskRequest;
import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.TaskSummary;
import com.edushift.modules.tasks.dto.UpdateTaskRequest;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.exception.DueAtInPastException;
import com.edushift.modules.tasks.exception.RecordEmptyPatchException;
import com.edushift.modules.tasks.exception.SectionNotFoundException;
import com.edushift.modules.tasks.exception.TaskNotFoundException;
import com.edushift.modules.tasks.mapper.TaskMapper;
import com.edushift.modules.tasks.repository.TaskRepository;
import com.edushift.modules.tasks.service.TaskService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

	private final TaskRepository taskRepository;
	private final SectionRepository sectionRepository;
	private final FileObjectService fileObjectService;
	private final TaskMapper taskMapper;

	@Override
	@Transactional
	public TaskResponse create(UUID sectionPublicUuid,
			CreateTaskRequest request, UUID ownerUserId) {
		Section section = requireSection(sectionPublicUuid);
		validateDueAt(request.dueAt());

		// Validate the attachment reference (cross-tenant protection):
		// the file must exist within the same tenant. A missing row
		// resolves as FILE_NOT_FOUND (BadRequest per spec REQ-TSK-01
		// edge case).
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
		entity.setTitle(request.title());
		entity.setDescription(request.description());
		entity.setDueAt(request.dueAt());
		entity.setAttachmentPublicUuid(attachmentPublicUuid);
		entity.setOwnerUserId(ownerUserId);
		entity.setAllowResubmission(true);
		Task saved = taskRepository.save(entity);

		// The task is the first consumer of the attached file.
		if (attachmentPublicUuid != null) {
			fileObjectService.acquireReference(attachmentPublicUuid);
		}

		return taskMapper.toResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<TaskSummary> listBySection(UUID sectionPublicUuid, Pageable pageable) {
		// Tenant-safe: if the section is not visible to the current tenant
		// (anti-enumeration), return an empty page instead of 404. The
		// section existence check still validates ownership internally
		// because findAllBySectionOrderByDueAtDesc uses @TenantId filtering.
		Optional<Section> section = sectionRepository.findByPublicUuid(sectionPublicUuid);
		if (section.isEmpty()) {
			return Page.empty(pageable);
		}
		return taskRepository
				.findAllBySectionOrderByDueAtDesc(section.get(), pageable)
				.map(taskMapper::toSummary);
	}

	@Override
	@Transactional(readOnly = true)
	public TaskResponse getByPublicUuid(UUID publicUuid) {
		return taskMapper.toResponse(requireTask(publicUuid));
	}

	@Override
	@Transactional
	public TaskResponse patch(UUID publicUuid, UpdateTaskRequest request) {
		if (isAllNull(request)) {
			throw new RecordEmptyPatchException();
		}
		Task entity = requireTask(publicUuid);

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

		Task saved = taskRepository.save(entity);
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

	private Task requireTask(UUID publicUuid) {
		return taskRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new TaskNotFoundException(publicUuid.toString()));
	}

	private Section requireSection(UUID sectionPublicUuid) {
		return sectionRepository.findByPublicUuid(sectionPublicUuid)
				.orElseThrow(() -> new SectionNotFoundException(sectionPublicUuid.toString()));
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
				&& r.allowResubmission() == null;
	}
}
