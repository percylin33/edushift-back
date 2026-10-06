package com.edushift.modules.tasks.submission.mapper;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tasks.submission.dto.SubmissionAttachmentResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionSummary;
import com.edushift.modules.tasks.submission.entity.Submission;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Submission} (Sprint 7a / BE-7a.2).
 */
@Component
@RequiredArgsConstructor
public class SubmissionMapper {

	private final UserRepository userRepository;
	private final StudentRepository studentRepository;
	private final FileObjectService fileObjectService;

	public SubmissionResponse toResponse(Submission entity) {
		return toResponse(entity, null);
	}

	public SubmissionResponse toResponse(Submission entity, Boolean wasIdempotent) {
		UUID studentUserPublicUuid = entity.getStudentUserId();
		UUID studentEntityPublicUuid = studentRepository.findByUserId(studentUserPublicUuid)
				.map(Student::getPublicUuid)
				.orElse(studentUserPublicUuid);
		SubmissionAttachmentResponse attachment = resolveAttachment(entity.getAttachmentPublicUuid());
		return new SubmissionResponse(
				entity.getPublicUuid(),
				entity.getTask() != null
						? entity.getTask().getPublicUuid() : null,
				studentEntityPublicUuid,
				entity.getSubmitterUserId(),
				entity.getTextBody(),
				entity.getAttachmentPublicUuid(),
				attachment,
				entity.getStatus(),
				entity.getGrade() != null ? entity.getGrade().intValue() : null,
				entity.getFeedback(),
				entity.getGradedByUserId(),
				entity.getGradedAt(),
				wasIdempotent,
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	public SubmissionSummary toSummary(Submission entity) {
		UUID studentUserPublicUuid = entity.getStudentUserId();
		UUID studentEntityPublicUuid = studentRepository.findByUserId(studentUserPublicUuid)
				.map(Student::getPublicUuid)
				.orElse(studentUserPublicUuid);
		SubmissionAttachmentResponse attachment = resolveAttachment(entity.getAttachmentPublicUuid());
		return new SubmissionSummary(
				entity.getPublicUuid(),
				studentEntityPublicUuid,
				resolveStudentDisplayName(studentUserPublicUuid),
				entity.getStatus(),
				entity.getGrade() != null ? entity.getGrade().intValue() : null,
				attachment != null,
				attachment,
				entity.getCreatedAt());
	}

	private SubmissionAttachmentResponse resolveAttachment(UUID attachmentPublicUuid) {
		if (attachmentPublicUuid == null) {
			return null;
		}
		return fileObjectService.findByPublicUuid(attachmentPublicUuid)
				.map(SubmissionAttachmentResponse::fromFileObject)
				.orElse(null);
	}

	private String resolveStudentDisplayName(UUID userPublicUuid) {
		return userRepository.findByPublicUuid(userPublicUuid)
				.map(this::userDisplayName)
				.or(() -> studentRepository.findByUserId(userPublicUuid).map(this::studentDisplayName))
				.filter(name -> !name.isBlank())
				.orElse("Estudiante");
	}

	private String userDisplayName(User user) {
		String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
		String last = user.getLastName() != null ? user.getLastName().trim() : "";
		return (first + " " + last).trim();
	}

	private String studentDisplayName(Student student) {
		String first = student.getFirstName() != null ? student.getFirstName().trim() : "";
		String last = student.getLastName() != null ? student.getLastName().trim() : "";
		String second = student.getSecondLastName() != null ? student.getSecondLastName().trim() : "";
		String full = (first + " " + last + (second.isEmpty() ? "" : " " + second)).trim();
		return full;
	}
}
