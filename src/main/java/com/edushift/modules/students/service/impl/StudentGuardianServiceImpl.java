package com.edushift.modules.students.service.impl;

import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.dto.UpdateGuardianLinkRequest;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.mapper.StudentGuardianMapper;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentGuardianServiceImpl implements StudentGuardianService {

	private final StudentRepository studentRepository;
	private final GuardianRepository guardianRepository;
	private final StudentGuardianRepository linkRepository;
	private final StudentGuardianMapper mapper;

	// ===========================================================================
	// Read
	// ===========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<GuardianResponse> listGuardians(UUID studentPublicUuid) {
		Student student = loadStudent(studentPublicUuid);
		return linkRepository.findActiveByStudentId(student.getId()).stream()
				.map(mapper::toResponse)
				.toList();
	}

	// ===========================================================================
	// Write
	// ===========================================================================

	@Override
	@Transactional
	public GuardianResponse addGuardian(UUID studentPublicUuid, AddGuardianRequest request) {
		Student student = loadStudent(studentPublicUuid);

		// Sibling sharing: reuse the guardian row if one already exists
		// for this document in this tenant. Adopt the request's profile
		// fields silently (the in-DB values stay authoritative; admins
		// who want to update the guardian's name should do so via a
		// future guardian-edit endpoint).
		Guardian guardian = guardianRepository
				.findByDocumentTypeAndDocumentNumber(request.documentType(), request.documentNumber())
				.orElseGet(() -> guardianRepository.save(mapper.newGuardianFromRequest(request)));

		// Reject duplicate active link for the same pair
		linkRepository.findActivePair(student.getId(), guardian.getId()).ifPresent(existing -> {
			throw new ConflictException("GUARDIAN_ALREADY_LINKED",
					"Guardian " + request.documentType() + " " + request.documentNumber()
							+ " is already linked to this student");
		});

		StudentGuardian link = new StudentGuardian();
		link.setStudent(student);
		link.setGuardian(guardian);
		link.setRelationship(request.relationship());
		link.setPrimaryContact(request.isPrimaryContact());
		link.setCanPickupStudent(request.canPickupStudent());

		StudentGuardian saved = linkRepository.save(link);
		log.info("[guardians] linked -- student={} guardian={} primary={} pickup={}",
				student.getPublicUuid(), guardian.getPublicUuid(),
				saved.isPrimaryContact(), saved.isCanPickupStudent());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public GuardianResponse updateLink(UUID studentPublicUuid, UUID guardianPublicUuid,
			UpdateGuardianLinkRequest request) {
		Student student = loadStudent(studentPublicUuid);
		Guardian guardian = loadGuardian(guardianPublicUuid);
		StudentGuardian link = loadLink(student, guardian);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(link);
		}

		boolean wasPrimary = link.isPrimaryContact();
		boolean willBePrimary = request.isPrimaryContact() != null
				? request.isPrimaryContact() : wasPrimary;

		// Primary-contact protection: if this link is currently the
		// only primary contact and the patch flips it off, reject —
		// the student would be left without a primary contact.
		if (wasPrimary && !willBePrimary) {
			ensureStillHasPrimary(student.getId(), link);
		}

		if (request.relationship() != null) {
			link.setRelationship(request.relationship());
		}
		if (request.isPrimaryContact() != null) {
			link.setPrimaryContact(request.isPrimaryContact());
		}
		if (request.canPickupStudent() != null) {
			link.setCanPickupStudent(request.canPickupStudent());
		}

		StudentGuardian saved = linkRepository.save(link);
		log.info("[guardians] link updated -- student={} guardian={} primary={} pickup={}",
				student.getPublicUuid(), guardian.getPublicUuid(),
				saved.isPrimaryContact(), saved.isCanPickupStudent());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public void unlinkGuardian(UUID studentPublicUuid, UUID guardianPublicUuid) {
		Student student = loadStudent(studentPublicUuid);
		Guardian guardian = loadGuardian(guardianPublicUuid);
		StudentGuardian link = loadLink(student, guardian);

		if (link.isPrimaryContact()) {
			ensureStillHasPrimary(student.getId(), link);
		}

		linkRepository.delete(link);   // soft delete via @SQLDelete
		log.info("[guardians] unlinked -- student={} guardian={}",
				student.getPublicUuid(), guardian.getPublicUuid());
	}

	// ===========================================================================
	// Internals
	// ===========================================================================

	private Student loadStudent(UUID publicUuid) {
		return studentRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Student", publicUuid));
	}

	private Guardian loadGuardian(UUID publicUuid) {
		return guardianRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Guardian", publicUuid));
	}

	private StudentGuardian loadLink(Student student, Guardian guardian) {
		return linkRepository.findActivePair(student.getId(), guardian.getId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"StudentGuardian link", student.getPublicUuid() + "/" + guardian.getPublicUuid()));
	}

	/**
	 * Ensures that removing / un-flagging {@code candidate} as the
	 * primary contact still leaves at least one other active primary
	 * contact for the student.
	 */
	private void ensureStillHasPrimary(UUID studentId, StudentGuardian candidate) {
		long primaryContacts = linkRepository.countActivePrimaryContacts(studentId);
		// `candidate` IS counted in the result; subtracting 1 simulates the
		// post-operation world.
		boolean candidateCountedAsPrimary = candidate.isPrimaryContact();
		long postOperation = candidateCountedAsPrimary ? primaryContacts - 1 : primaryContacts;
		if (postOperation < 1) {
			throw new BusinessException("LAST_PRIMARY_CONTACT",
					"A student must have at least one primary-contact guardian; "
							+ "promote another guardian first");
		}
	}
}
