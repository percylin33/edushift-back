package com.edushift.modules.privacy.service.impl;

import com.edushift.modules.privacy.dto.ConsentResponse;
import com.edushift.modules.privacy.dto.CreateConsentRequest;
import com.edushift.modules.privacy.entity.PrivacyConsent;
import com.edushift.modules.privacy.repository.PrivacyConsentRepository;
import com.edushift.modules.privacy.service.PrivacyConsentService;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrivacyConsentServiceImpl implements PrivacyConsentService {

	private final PrivacyConsentRepository repository;
	private final StudentRepository studentRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final CurrentUserProvider currentUserProvider;
	private final AuditLogger auditLogger;

	@Override
	@Transactional
	public ConsentResponse recordConsent(CreateConsentRequest request, String ipAddress, String userAgent) {
		UUID guardianUserId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Usuario no autenticado"));
		Student student = findLinkedStudent(request.studentPublicUuid(), guardianUserId);

		PrivacyConsent consent = new PrivacyConsent();
		consent.setGuardianUserId(guardianUserId);
		consent.setStudentId(student.getId());
		consent.setConsentGranted(request.consentGranted());
		consent.setPolicyVersion(request.policyVersion().trim());
		consent.setPurposeCode(request.purposeCode().trim());
		consent.setNoticeSnapshot(request.noticeSnapshot().trim());
		consent.setGrantedAt(Instant.now());
		consent.setIpAddress(ipAddress);
		consent.setUserAgent(userAgent);

		PrivacyConsent saved = repository.save(consent);
		auditLogger.log(AuditAction.CREATE, "PrivacyConsent", saved.getPublicUuid(),
				"Se registró la decisión de consentimiento del apoderado",
				Map.of("studentPublicUuid", student.getPublicUuid(), "policyVersion", saved.getPolicyVersion(),
						"consentGranted", saved.getConsentGranted()), "privacy");
		return mapToResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public ConsentResponse getLatestConsent(UUID studentPublicUuid) {
		UUID guardianUserId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Usuario no autenticado"));
		Student student = studentRepository.findByPublicUuid(studentPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Student", studentPublicUuid));
		PrivacyConsent consent = repository.findTopByStudentIdAndGuardianUserIdOrderByGrantedAtDesc(
					student.getId(), guardianUserId)
				.orElseThrow(() -> new ResourceNotFoundException("No se encontró consentimiento registrado para el estudiante."));
		auditLogger.log(AuditAction.READ, "PrivacyConsent", consent.getPublicUuid(),
				"El apoderado consultó su registro de consentimiento",
				Map.of("studentPublicUuid", student.getPublicUuid()), "privacy");
		return mapToResponse(consent);
	}

	@Override
	@Transactional
	public ConsentResponse revokeConsent(UUID consentPublicUuid) {
		UUID guardianUserId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Usuario no autenticado"));
		PrivacyConsent consent = repository.findByPublicUuid(consentPublicUuid)
				.filter(item -> item.getGuardianUserId().equals(guardianUserId))
				.orElseThrow(() -> new ResourceNotFoundException("PrivacyConsent", consentPublicUuid));
		if (consent.getConsentGranted() && consent.getRevokedAt() == null) {
			consent.setRevokedAt(Instant.now());
			consent.setConsentGranted(false);
			repository.save(consent);
			auditLogger.log(AuditAction.CUSTOM, "PrivacyConsent", consent.getPublicUuid(),
					"El apoderado revocó su consentimiento",
					Map.of("policyVersion", consent.getPolicyVersion()), "privacy");
		}
		return mapToResponse(consent);
	}

	private Student findLinkedStudent(UUID studentPublicUuid, UUID guardianUserId) {
		Student student = studentRepository.findByPublicUuid(studentPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Student", studentPublicUuid));
		if (!studentGuardianRepository.existsActiveLinkForParent(student.getId(), guardianUserId)) {
			// Do not reveal whether an unlinked student exists in this tenant.
			throw new ResourceNotFoundException("Student", studentPublicUuid);
		}
		return student;
	}

	private ConsentResponse mapToResponse(PrivacyConsent consent) {
		return new ConsentResponse(
				consent.getPublicUuid(),
				consent.getGuardianUserId(),
				studentRepository.findById(consent.getStudentId())
						.map(Student::getPublicUuid)
						.orElseThrow(() -> new ResourceNotFoundException("Student", consent.getStudentId())),
				consent.getPolicyVersion(),
				consent.getPurposeCode(),
				consent.getNoticeSnapshot(),
				consent.getConsentGranted(),
				consent.getGrantedAt(),
				consent.getRevokedAt()
		);
	}
}
