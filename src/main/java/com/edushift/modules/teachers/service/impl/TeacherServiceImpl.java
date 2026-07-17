package com.edushift.modules.teachers.service.impl;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.dto.CreateTeacherRequest;
import com.edushift.modules.teachers.dto.InviteTeacherResponse;
import com.edushift.modules.teachers.dto.LinkTeacherUserRequest;
import com.edushift.modules.teachers.dto.TeacherListItem;
import com.edushift.modules.teachers.dto.TeacherResponse;
import com.edushift.modules.teachers.dto.UpdateTeacherRequest;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.mapper.TeacherMapper;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.teachers.service.TeacherService;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.service.UserInvitationService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link TeacherService}.
 *
 * <h3>Invitation hook</h3>
 * {@link #invite(UUID)} delegates to {@code UserInvitationService.createInvitation}
 * with {@code metadata = { "teacherId": <internal-id> }}. The
 * {@code TeacherInvitationListener} (in {@code listener/}) consumes the
 * synchronous {@code InvitationAcceptedEvent} fired inside the same
 * transaction as the new user creation and links {@code teacher.user_id}
 * to the freshly-created user — so the invariant "accept invitation
 * ⇒ teacher linked" is atomic with "user created".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

	/** Convention key carried in {@code user_invitations.metadata}. */
	public static final String METADATA_TEACHER_ID_KEY = "teacherId";

	private final TeacherRepository teacherRepository;
	private final UserRepository userRepository;
	private final UserInvitationService invitationService;
	private final TeacherAssignmentRepository assignmentRepository;
	private final TeacherMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public Page<TeacherListItem> listTeachers(String search,
			EmploymentStatus employmentStatus, Boolean hasUserAccount,
			Pageable pageable) {
		// Always pass a String (never null): the repo query relies on
		// concat('%', :search, '%') as a no-op when search is empty, to
		// dodge the Hibernate/PostgreSQL "lower(bytea)" parameter-typing
		// trap on null string params.
		String normalised = (search == null || search.isBlank()) ? "" : search.trim();
		return teacherRepository
				.findFiltered(normalised, employmentStatus, hasUserAccount, pageable)
				.map(mapper::toListItem);
	}

	@Override
	@Transactional(readOnly = true)
	public TeacherResponse getTeacher(UUID publicUuid) {
		return mapper.toResponse(loadTeacher(publicUuid));
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public TeacherResponse createTeacher(CreateTeacherRequest request) {
		ensureDocumentAvailable(request.documentType().name(),
				request.documentNumber().trim(), null);

		String email = request.email() == null ? null : request.email().trim().toLowerCase();
		if (email != null && !email.isEmpty()) {
			ensureEmailAvailable(email, null);
		}

		Teacher teacher = mapper.fromCreate(request);
		try {
			Teacher saved = teacherRepository.saveAndFlush(teacher);
			log.info("[teachers] created -- publicUuid={} document={}/{} email='{}'",
					saved.getPublicUuid(), saved.getDocumentType(),
					saved.getDocumentNumber(), saved.getEmail());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// Race-condition fallback: the partial unique indexes guarantee
			// we can't have a stale read. Surface the most likely cause.
			throw new ConflictException("TEACHER_DOCUMENT_TAKEN",
					"Another teacher in this tenant already uses document '"
							+ request.documentType() + " " + request.documentNumber() + "'", ex);
		}
	}

	@Override
	@Transactional
	public TeacherResponse updateTeacher(UUID publicUuid, UpdateTeacherRequest request) {
		Teacher teacher = loadTeacher(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(teacher);
		}

		// Document collision (only if the patch changes either field)
		String newType = request.documentType() != null
				? request.documentType().name()
				: teacher.getDocumentType().name();
		String newNumber = request.documentNumber() != null
				? request.documentNumber().trim()
				: teacher.getDocumentNumber();
		if (request.documentType() != null || request.documentNumber() != null) {
			boolean changed = !(newType.equals(teacher.getDocumentType().name())
					&& newNumber.equalsIgnoreCase(teacher.getDocumentNumber()));
			if (changed) {
				ensureDocumentAvailable(newType, newNumber, teacher.getId());
			}
		}

		// Email collision (only if the patch changes it)
		if (request.email() != null) {
			String trimmed = request.email().trim();
			String normalised = trimmed.isEmpty() ? null : trimmed.toLowerCase();
			if (normalised != null
					&& !normalised.equalsIgnoreCase(teacher.getEmail())) {
				ensureEmailAvailable(normalised, teacher.getId());
			}
		}

		mapper.applyUpdate(request, teacher);

		try {
			Teacher saved = teacherRepository.saveAndFlush(teacher);
			log.info("[teachers] updated -- publicUuid={}", saved.getPublicUuid());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("TEACHER_DOCUMENT_TAKEN",
					"Another teacher in this tenant already uses the document or email", ex);
		}
	}

	@Override
	@Transactional
	public TeacherResponse linkUser(UUID publicUuid, LinkTeacherUserRequest request) {
		Teacher teacher = loadTeacher(publicUuid);
		if (teacher.getUserId() != null) {
			throw new ConflictException("TEACHER_ALREADY_HAS_USER",
					"Teacher '" + teacher.fullName() + "' is already linked to a user");
		}

		User user = userRepository.findByPublicUuid(request.userPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("User", request.userPublicUuid()));

		if (!user.hasRole(UserRole.TEACHER)) {
			throw new ConflictException("USER_NOT_TEACHER_ROLE",
					"Cannot link user '" + user.getEmail()
							+ "': missing TEACHER role");
		}

		teacherRepository.findByUserId(user.getPublicUuid()).ifPresent(other -> {
			throw new ConflictException("USER_ALREADY_LINKED_TO_TEACHER",
					"User '" + user.getEmail() + "' is already linked to another teacher");
		});

		// DEBT-FK-BUGS-3 / V77: teachers.user_id FK now points at
		// users.public_uuid (not users.id). The repo lookup above and
		// the write below both use publicUuid.
		teacher.setUserId(user.getPublicUuid());
		try {
			Teacher saved = teacherRepository.saveAndFlush(teacher);
			log.info("[teachers] link-user -- teacher={} user={}",
					saved.getPublicUuid(), user.getPublicUuid());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// Race-condition double-link
			throw new ConflictException("USER_ALREADY_LINKED_TO_TEACHER",
					"User '" + user.getEmail() + "' is already linked to another teacher", ex);
		}
	}

	@Override
	@Transactional
	public InviteTeacherResponse invite(UUID publicUuid) {
		Teacher teacher = loadTeacher(publicUuid);

		if (teacher.getUserId() != null) {
			throw new ConflictException("TEACHER_ALREADY_HAS_USER",
					"Teacher '" + teacher.fullName() + "' already has a linked user");
		}
		if (teacher.getEmail() == null || teacher.getEmail().isBlank()) {
			throw new BusinessException("TEACHER_NEEDS_EMAIL_TO_INVITE",
					"Teacher '" + teacher.fullName() + "' needs an email to be invited");
		}

		Map<String, Object> metadata = new HashMap<>();
		metadata.put(METADATA_TEACHER_ID_KEY, teacher.getId().toString());

		InvitationResponse response = invitationService.createInvitation(
				new CreateInvitationRequest(
						teacher.getEmail(),
						teacher.getFirstName(),
						teacher.getLastName(),
						Set.of(UserRole.TEACHER.name()),
						metadata));

		log.info("[teachers] invited -- teacher={} email='{}' invitationId={}",
				teacher.getPublicUuid(), teacher.getEmail(), response.publicUuid());

		return new InviteTeacherResponse(
				response.publicUuid(),
				response.token(),
				response.expiresAt(),
				teacher.getPublicUuid(),
				teacher.getEmail());
	}

	@Override
	@Transactional
	public void deleteTeacher(UUID publicUuid) {
		Teacher teacher = loadTeacher(publicUuid);

		// BE-4.7: reject delete when there are active assignments. The
		// FK on teacher_assignments(teacher_id) is RESTRICT — without
		// this check we would surface a generic 500 on integrity
		// violation. Closing soft-end the assignments first is intentional
		// admin work, hence 409 with a clear code.
		if (assignmentRepository.existsActiveByTeacher(teacher)) {
			throw new ConflictException("TEACHER_HAS_ACTIVE_ASSIGNMENTS",
					"Teacher '" + teacher.fullName() + "' has active assignments. "
							+ "Soft-end them first via DELETE /assignments/{uuid}.");
		}

		teacherRepository.delete(teacher);
		log.info("[teachers] deleted -- publicUuid={} document={}/{}",
				teacher.getPublicUuid(),
				teacher.getDocumentType(),
				teacher.getDocumentNumber());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private Teacher loadTeacher(UUID publicUuid) {
		return teacherRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Teacher", publicUuid));
	}

	private void ensureDocumentAvailable(String type, String number, UUID excludeInternalId) {
		teacherRepository.findByDocument(
						com.edushift.modules.students.entity.DocumentType.fromName(type),
						number)
				.filter(existing -> !Objects.equals(existing.getId(), excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("TEACHER_DOCUMENT_TAKEN",
							"Another teacher in this tenant already uses document '"
									+ type + " " + number + "'");
				});
	}

	private void ensureEmailAvailable(String emailLower, UUID excludeInternalId) {
		teacherRepository.findByEmailIgnoreCase(emailLower)
				.filter(existing -> !Objects.equals(existing.getId(), excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("TEACHER_EMAIL_TAKEN",
							"Another teacher in this tenant already uses email '"
									+ emailLower + "'");
				});
	}
}
