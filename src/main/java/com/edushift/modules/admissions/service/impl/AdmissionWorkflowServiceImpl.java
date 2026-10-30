package com.edushift.modules.admissions.service.impl;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.admissions.dto.CreatePresentialAdmissionRequest;
import com.edushift.modules.admissions.dto.CreatePresentialInquiryRequest;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.admissions.dto.AdmissionApplicationResponse;
import com.edushift.modules.admissions.dto.AdmissionInquiryResponse;
import com.edushift.modules.admissions.dto.AdmissionInquiryEventResponse;
import com.edushift.modules.admissions.dto.CompleteAdmissionEnrollmentRequest;
import com.edushift.modules.admissions.dto.CreateAdmissionFromInquiryRequest;
import com.edushift.modules.admissions.dto.OfferLinkResponse;
import com.edushift.modules.admissions.dto.PublicAdmissionInquiryRequest;
import com.edushift.modules.admissions.dto.PublicAdmissionCatalogResponse;
import com.edushift.modules.admissions.dto.PublicInquiryReceipt;
import com.edushift.modules.admissions.dto.PublicOfferResponse;
import com.edushift.modules.admissions.dto.UpdateAdmissionInquiryRequest;
import com.edushift.modules.admissions.entity.AdmissionApplication;
import com.edushift.modules.admissions.entity.AdmissionApplicationStatus;
import com.edushift.modules.admissions.entity.AdmissionContactType;
import com.edushift.modules.admissions.entity.AdmissionInquiry;
import com.edushift.modules.admissions.entity.AdmissionInquirySource;
import com.edushift.modules.admissions.entity.AdmissionInquiryChannel;
import com.edushift.modules.admissions.entity.AdmissionInquiryEvent;
import com.edushift.modules.admissions.entity.AdmissionInquiryStatus;
import com.edushift.modules.admissions.entity.AdmissionOfferStatus;
import com.edushift.modules.admissions.entity.AdmissionStatusEvent;
import com.edushift.modules.admissions.repository.AdmissionApplicationRepository;
import com.edushift.modules.admissions.repository.AdmissionInquiryRepository;
import com.edushift.modules.admissions.repository.AdmissionInquiryEventRepository;
import com.edushift.modules.admissions.repository.AdmissionStatusEventRepository;
import com.edushift.modules.admissions.service.AdmissionWorkflowService;
import com.edushift.modules.admissions.service.AdmissionReservationService;
import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.enrollments.dto.CreateEnrollmentRequest;
import com.edushift.modules.students.enrollments.service.StudentEnrollmentService;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.modules.students.service.StudentService;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped admissions workflow, called only after tenant context is established. */
@Service
@RequiredArgsConstructor
public class AdmissionWorkflowServiceImpl implements AdmissionWorkflowService {

	private final com.edushift.modules.admissions.mapper.AdmissionApplicantMapper applicantMapper;
	private final AdmissionInquiryRepository inquiryRepository;
	private final AdmissionInquiryEventRepository inquiryEventRepository;
	private final AdmissionApplicationRepository applicationRepository;
	private final AdmissionStatusEventRepository eventRepository;
	private final AcademicYearRepository yearRepository;
	private final SectionRepository sectionRepository;
	private final GradeRepository gradeRepository;
	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final StudentGuardianService guardianService;
	private final StudentEnrollmentService enrollmentService;
	private final CurrentUserProvider currentUserProvider;
	private final AdmissionReservationService reservationService;

	@Override
	@Transactional(readOnly = true)
	public PublicAdmissionCatalogResponse publicCatalog(String tenantName) {
		var years = yearRepository.findAllByOrderByStartDateDesc().stream()
				.filter(year -> year.getStatus() != AcademicYearStatus.CLOSED)
				.map(year -> new PublicAdmissionCatalogResponse.YearOption(year.getPublicUuid(), year.getName()))
				.toList();
		var grades = gradeRepository.findAll().stream()
				.sorted(Comparator.comparing((Grade grade) -> grade.getLevel().getOrdinal())
						.thenComparing(Grade::getOrdinal))
				.map(grade -> new PublicAdmissionCatalogResponse.GradeOption(
						grade.getPublicUuid(), grade.getName(), grade.getLevel().getName()))
				.toList();
		return new PublicAdmissionCatalogResponse(tenantName, years, grades);
	}

	@Override
	@Transactional
	public PublicInquiryReceipt submitPublicInquiry(PublicAdmissionInquiryRequest request) {
		if (!request.privacyAccepted()) {
			throw new BadRequestException("ADMISSION_PRIVACY_ACCEPTANCE_REQUIRED",
					"Acepta el aviso de privacidad para enviar la consulta.");
		}
		if (request.website() != null && !request.website().isBlank()) {
			return new PublicInquiryReceipt(UUID.randomUUID(), "RECEIVED");
		}
		if (request.contactName() == null || request.contactName().isBlank()) {
			throw new BadRequestException("ADMISSION_CONTACT_NAME_REQUIRED", "Ingresa el nombre de la familia.");
		}
		if (request.contactType() != AdmissionContactType.PHONE) {
			throw new BadRequestException("ADMISSION_PHONE_REQUIRED", "Indica un celular o teléfono de contacto.");
		}
		String phone = validatedPhone(request.contactValue());
		AcademicYear year = yearRepository.findByPublicUuid(request.academicYearPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.academicYearPublicUuid()));
		Grade grade = gradeRepository.findByPublicUuid(request.gradePublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("Grade", request.gradePublicUuid()));
		AdmissionInquiry inquiry = new AdmissionInquiry();
		inquiry.setAcademicYear(year);
		inquiry.setGrade(grade);
		inquiry.setContactName(request.contactName().trim());
		inquiry.setContactType(request.contactType());
		inquiry.setContactValue(phone);
		inquiry.setPrivacyAcceptedAt(Instant.now());
		inquiry.setPrivacyNoticeGivenAt(Instant.now());
		inquiry.setSource(AdmissionInquirySource.WEBSITE);
		inquiry.setInitialChannel(AdmissionInquiryChannel.WEBSITE);
		AdmissionInquiry saved = inquiryRepository.saveAndFlush(inquiry);
		appendInquiryEvent(saved, null, AdmissionInquiryStatus.RECEIVED, "Consulta recibida desde el formulario público.", AdmissionInquiryEvent.ActorType.SYSTEM);
		return new PublicInquiryReceipt(saved.getPublicUuid(), saved.getStatus().name());
	}

	@Override
	@Transactional
	public AdmissionInquiryResponse createPresentialInquiry(UUID academicYearPublicUuid,
			CreatePresentialInquiryRequest request) {
		AcademicYear year = yearRepository.findByPublicUuid(academicYearPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearPublicUuid));
		if (year.getStatus() == AcademicYearStatus.CLOSED) {
			throw new ConflictException("ADMISSION_YEAR_CLOSED", "El año académico está cerrado.");
		}
		Grade grade = gradeRepository.findByPublicUuid(request.gradePublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("Grade", request.gradePublicUuid()));
		String contactValue = request.contactValue().trim();
		if (request.contactType() == AdmissionContactType.PHONE) {
			contactValue = validatedPhone(contactValue);
		}
		else if (!contactValue.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
			throw new BadRequestException("ADMISSION_EMAIL_INVALID", "Ingresa un correo válido.");
		}
		AdmissionInquiry inquiry = new AdmissionInquiry();
		inquiry.setAcademicYear(year);
		inquiry.setGrade(grade);
		inquiry.setContactName(request.contactName().trim());
		inquiry.setContactType(request.contactType());
		inquiry.setContactValue(contactValue);
		inquiry.setSource(AdmissionInquirySource.PRESENTIAL);
		inquiry.setInitialChannel(request.initialChannel());
		inquiry.setPrivacyNoticeGivenAt(request.privacyNoticeGiven() ? Instant.now() : null);
		inquiry.setPrivacyAcceptedAt(null);
		inquiry.setNote(blankToNull(request.note()));
		inquiry.setResponsibleName(blankToNull(request.responsibleName()));
		inquiry.setNextAttentionDate(request.nextAttentionDate());
		inquiry.setNextAction(blankToNull(request.nextAction()));
		AdmissionInquiry saved = inquiryRepository.saveAndFlush(inquiry);
		appendInquiryEvent(saved, null, AdmissionInquiryStatus.RECEIVED,
				"Consulta registrada por atención " + request.initialChannel().name().toLowerCase() + ".",
				AdmissionInquiryEvent.ActorType.STAFF);
		return toInquiryResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdmissionInquiryResponse> listInquiries(UUID academicYearPublicUuid) {
		AcademicYear year = findYear(academicYearPublicUuid);
		return inquiryRepository.findAllByAcademicYearOrderByCreatedAtDesc(year).stream()
				.map(this::toInquiryResponse).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdmissionInquiryEventResponse> inquiryHistory(UUID inquiryPublicUuid) {
		AdmissionInquiry inquiry = findInquiry(inquiryPublicUuid);
		return inquiryEventRepository.findAllByInquiryOrderByCreatedAtAsc(inquiry).stream()
				.map(event -> new AdmissionInquiryEventResponse(event.getFromStatus(), event.getToStatus(),
						event.getNote(), event.getActorUserId(), event.getActorType(), event.getCreatedAt()))
				.toList();
	}

	@Override
	@Transactional
	public AdmissionInquiryResponse updateInquiry(UUID inquiryPublicUuid, UpdateAdmissionInquiryRequest request) {
		AdmissionInquiry inquiry = inquiryRepository.findByPublicUuidForUpdate(inquiryPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionInquiry", inquiryPublicUuid));
		if (inquiry.getStatus() == AdmissionInquiryStatus.CONVERTED) {
			throw new ConflictException("ADMISSION_INQUIRY_ALREADY_CONVERTED", "La consulta ya se convirtió en postulación.");
		}
        if (inquiry.getStatus() == AdmissionInquiryStatus.CLOSED) {
            throw new ConflictException("ADMISSION_INQUIRY_CLOSED", "La consulta está cerrada.");
        }
        if (request.status() == AdmissionInquiryStatus.CLOSED && blankToNull(request.note()) == null) {
            throw new BadRequestException("ADMISSION_REASON_REQUIRED", "Indica el motivo del cierre.");
        }
        if (request.updateFollowUp() && blankToNull(request.note()) == null) {
            throw new BadRequestException("ADMISSION_REASON_REQUIRED", "Registra una nota para el cambio de seguimiento.");
        }
        if (request.updateFollowUp()) {
            inquiry.setResponsibleName(blankToNull(request.responsibleName()));
            inquiry.setNextAttentionDate(request.nextAttentionDate());
            inquiry.setNextAction(blankToNull(request.nextAction()));
        }
		AdmissionInquiryStatus previousStatus = inquiry.getStatus();
		if (request.status() != null && request.status() != previousStatus) {
			if (!inquiryTransitionAllowed(previousStatus, request.status())) {
				throw new ConflictException("ADMISSION_INQUIRY_INVALID_TRANSITION", "No se permite ese cambio de estado.");
			}
			inquiry.setStatus(request.status());
		}
		if (request.note() != null) inquiry.setNote(blankToNull(request.note()));
        if (inquiry.getStatus() == AdmissionInquiryStatus.CLOSED) {
            inquiry.setNextAttentionDate(null); inquiry.setNextAction(null);
        }
		AdmissionInquiry saved = inquiryRepository.save(inquiry);
		if (saved.getStatus() != previousStatus || blankToNull(request.note()) != null) {
            appendInquiryEvent(saved, previousStatus, saved.getStatus(), blankToNull(request.note()), AdmissionInquiryEvent.ActorType.STAFF);
            if (request.updateFollowUp()) {
                String followUpNote = "Seguimiento: " + (saved.getResponsibleName() == null ? "sin responsable" : saved.getResponsibleName())
                    + " · Próxima atención: " + (saved.getNextAttentionDate() == null ? "sin fecha" : saved.getNextAttentionDate())
                    + " · Acción: " + (saved.getNextAction() == null ? "sin acción pendiente" : saved.getNextAction());
                appendInquiryEvent(saved, saved.getStatus(), saved.getStatus(), followUpNote, AdmissionInquiryEvent.ActorType.STAFF);
            }
		}
		return toInquiryResponse(saved);
	}

	@Override
	@Transactional
	public AdmissionApplicationResponse convertInquiry(UUID inquiryPublicUuid,
			CreateAdmissionFromInquiryRequest request) {
		AdmissionInquiry inquiry = inquiryRepository.findByPublicUuidForUpdate(inquiryPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionInquiry", inquiryPublicUuid));
		if (inquiry.getConvertedApplicationId() != null) {
			return applicationRepository.findScopedById(inquiry.getConvertedApplicationId())
					.map(this::toApplicationResponse)
					.orElseThrow(() -> new ResourceNotFoundException("AdmissionApplication", inquiryPublicUuid));
		}
		if (inquiry.getStatus() == AdmissionInquiryStatus.CLOSED || inquiry.getStatus() == AdmissionInquiryStatus.CONVERTED) {
			throw new ConflictException("ADMISSION_INQUIRY_CLOSED", "La consulta está cerrada y no se puede convertir.");
		}
		AdmissionInquiryStatus previous = inquiry.getStatus();
		AcademicYear year = request.academicYearPublicUuid() == null ? inquiry.getAcademicYear() : findYear(request.academicYearPublicUuid());
		Grade grade = request.gradePublicUuid() == null ? inquiry.getGrade()
				: gradeRepository.findByPublicUuid(request.gradePublicUuid())
						.orElseThrow(() -> new ResourceNotFoundException("Grade", request.gradePublicUuid()));
		AdmissionApplication application = createFormalApplication(year, grade, inquiry, request);
		inquiry.setStatus(AdmissionInquiryStatus.CONVERTED);
        inquiry.setNextAttentionDate(null); inquiry.setNextAction(null);
		inquiry.setConvertedApplicationId(application.getId());
		inquiryRepository.save(inquiry);
		appendInquiryEvent(inquiry, previous, AdmissionInquiryStatus.CONVERTED,
				"Se inició la admisión desde la consulta.", AdmissionInquiryEvent.ActorType.STAFF);
		return toApplicationResponse(application);
	}

	@Override
	@Transactional
	public AdmissionApplicationResponse createPresential(UUID yearPublicUuid,
			CreatePresentialAdmissionRequest request) {
		AcademicYear year = findYear(yearPublicUuid);
		Grade grade = gradeRepository.findByPublicUuid(request.gradePublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("Grade", request.gradePublicUuid()));
		return toApplicationResponse(createFormalApplication(year, grade, null, request.applicant()));
	}

	private AdmissionApplication createFormalApplication(AcademicYear year, Grade grade,
			AdmissionInquiry inquiry, CreateAdmissionFromInquiryRequest request) {
		// Serialize applicant creation in the same year, including simultaneous visits/conversions.
		yearRepository.findByPublicUuidForUpdate(year.getPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", year.getPublicUuid()));
		if (year.getStatus() == AcademicYearStatus.CLOSED) {
			throw new ConflictException("ADMISSION_YEAR_CLOSED", "El año académico está cerrado.");
		}
		String studentDocument = request.studentDocumentNumber().trim().toUpperCase();
		if (applicationRepository.existsByAcademicYearAndStudentDocumentTypeAndStudentDocumentNumber(
				year, request.studentDocumentType(), studentDocument)) {
			throw new ConflictException("ADMISSION_APPLICANT_ALREADY_EXISTS", "Ya existe una postulación con el documento del estudiante para este año.");
		}
		AdmissionApplication application = new AdmissionApplication();
		application.setAcademicYear(year);
		application.setGrade(grade);
		application.setInquiry(inquiry);
		application.setSource(request.source());
		application.setStatus(AdmissionApplicationStatus.RECEIVED);
		application.setLastStatusNote(blankToNull(request.note()));
        applicantMapper.update(application, request);
		application = applicationRepository.saveAndFlush(application);
		appendStatusEvent(application, null, AdmissionApplicationStatus.RECEIVED,
				inquiry == null ? "Admisión presencial registrada." : "Admisión iniciada desde consulta.");
		return application;
	}

	@Override
	public OfferLinkResponse issueOfferLink(UUID applicationPublicUuid) {
		throw confirmationRetired();
	}

	@Override
	public PublicOfferResponse getPublicOffer(String tenantName, String token) {
		throw confirmationRetired();
	}

	@Override
	public PublicOfferResponse respondToOffer(String token, boolean accepted) {
		throw confirmationRetired();
	}

	@Override
	public void openOffer(AdmissionApplication application) {
		// Historical offer fields remain untouched; new admissions need no family response.
	}

	@Override
	public void processDueOfferReminders() {
		// Compatibility entry point: family confirmation reminders have been retired.
	}

	private ConflictException confirmationRetired() {
		return new ConflictException("ADMISSION_FAMILY_CONFIRMATION_RETIRED",
				"Ya no se requiere confirmación familiar. Comunícate con el colegio para continuar la admisión.");
	}

	@Override
	@Transactional
	public AdmissionApplicationResponse completeEnrollment(UUID applicationPublicUuid,
			CompleteAdmissionEnrollmentRequest request) {
		AdmissionApplication application = findApplication(applicationPublicUuid);
		if (application.getStatus() == AdmissionApplicationStatus.ENROLLED) return toApplicationResponse(application);
		if (application.getStatus() != AdmissionApplicationStatus.ADMITTED) {
			throw new ConflictException("ADMISSION_NOT_READY_TO_ENROLL", "La postulación debe estar admitida antes de matricular.");
		}
		var section = sectionRepository.findByPublicUuid(request.sectionPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("Section", request.sectionPublicUuid()));
		if (!section.getAcademicYear().getPublicUuid().equals(application.getAcademicYear().getPublicUuid())
				|| !section.getGrade().getPublicUuid().equals(application.getGrade().getPublicUuid())) {
			throw new ConflictException("ADMISSION_SECTION_MISMATCH", "La sección debe corresponder al año y grado de la solicitud.");
		}
		if (application.getStudent() == null) {
			Student student = createStudentAndGuardian(application);
			application.setStudent(student);
			applicationRepository.saveAndFlush(application);
		}
		var enrollment = enrollmentService.createEnrollment(application.getStudent().getPublicUuid(),
				new CreateEnrollmentRequest(request.sectionPublicUuid(), application.getAcademicYear().getPublicUuid(),
						request.enrolledAt(), request.notes()));
		application.setEnrollmentPublicUuid(enrollment.publicUuid());
		application.setStatus(AdmissionApplicationStatus.ENROLLED);
		application.setLastStatusNote(blankToNull(request.notes()));
		applicationRepository.save(application);
		appendStatusEvent(application, AdmissionApplicationStatus.ADMITTED, AdmissionApplicationStatus.ENROLLED,
				blankToNull(request.notes()));
		reservationService.consumeOnEnrollment(application);
		return toApplicationResponse(application);
	}

	@Override
	@Transactional(readOnly = true)
	public AdmissionApplicationResponse enrolledApplicationForRetry(UUID applicationPublicUuid) {
		AdmissionApplication application = findApplication(applicationPublicUuid);
		if (application.getStatus() != AdmissionApplicationStatus.ENROLLED
				|| application.getStudent() == null || application.getEnrollmentPublicUuid() == null) {
			throw new ConflictException("ADMISSION_NOT_ENROLLED", "La postulación no tiene una matrícula completada para reintentar sus tareas pendientes.");
		}
		return toApplicationResponse(application);
	}

	private Student createStudentAndGuardian(AdmissionApplication application) {
		if (application.getStudentDocumentType() == null || application.getStudentDocumentNumber() == null
				|| application.getStudentFirstName() == null || application.getStudentLastName() == null
				|| application.getGuardianDocumentType() == null || application.getGuardianDocumentNumber() == null
				|| application.getGuardianRelationship() == null) {
			throw new ConflictException("ADMISSION_APPLICANT_DATA_INCOMPLETE", "Completa los datos del estudiante y apoderado antes de matricular.");
		}
		Student existing = studentRepository.findByDocumentTypeAndDocumentNumber(
				application.getStudentDocumentType(), application.getStudentDocumentNumber()).orElse(null);
		if (existing != null) {
			boolean linked = guardianService.listGuardians(existing.getPublicUuid()).stream().anyMatch(guardian ->
					guardian.documentType() == application.getGuardianDocumentType()
					&& guardian.documentNumber().equals(application.getGuardianDocumentNumber()));
			if (!linked) addApplicantGuardian(existing.getPublicUuid(), application);
			return existing;
		}
		var createdStudent = studentService.createStudent(new com.edushift.modules.students.dto.CreateStudentRequest(
				application.getStudentDocumentType(), application.getStudentDocumentNumber(),
				application.getStudentFirstName(), application.getStudentLastName(),
				application.getStudentSecondLastName(), application.getStudentBirthDate(),
				application.getStudentGender() == null ? Gender.NOT_SPECIFIED : application.getStudentGender(),
				null, null, null, null, null, null));
		addApplicantGuardian(createdStudent.publicUuid(), application);
		return studentRepository.findByPublicUuid(createdStudent.publicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("Student", createdStudent.publicUuid()));
	}

	private void addApplicantGuardian(UUID studentPublicUuid, AdmissionApplication application) {
		guardianService.addGuardian(studentPublicUuid, new AddGuardianRequest(
				application.getGuardianDocumentType(), application.getGuardianDocumentNumber(),
				application.getGuardianFirstName(), application.getGuardianLastName(),
				application.getGuardianEmail(), application.getGuardianPhone(), null,
				application.getGuardianRelationship(), true, false));
	}

	private AdmissionInquiryResponse toInquiryResponse(AdmissionInquiry inquiry) {
		AdmissionApplication converted = inquiry.getConvertedApplicationId() == null ? null
				: applicationRepository.findScopedById(inquiry.getConvertedApplicationId()).orElse(null);
		UUID convertedPublicUuid = converted == null ? null : converted.getPublicUuid();
		return new AdmissionInquiryResponse(inquiry.getPublicUuid(), inquiry.getAcademicYear().getPublicUuid(),
				inquiry.getAcademicYear().getName(), inquiry.getGrade().getPublicUuid(), inquiry.getGrade().getName(),
				inquiry.getContactName(), inquiry.getContactType(), inquiry.getContactValue(), inquiry.getStatus(),
				inquiry.getNote(), convertedPublicUuid, inquiry.getCreatedAt(), converted == null ? null : converted.getStatus(),
				converted == null ? null : converted.getAcademicYear().getPublicUuid(), inquiry.getResponsibleName(), inquiry.getNextAttentionDate(), inquiry.getNextAction(),
				inquiry.getSource(), inquiry.getInitialChannel(), inquiry.getPrivacyNoticeGivenAt());
	}

	private AdmissionApplicationResponse toApplicationResponse(AdmissionApplication application) {
		String name = application.getStudent() == null ? applicantName(application) : application.getStudent().fullName();
		return new AdmissionApplicationResponse(application.getPublicUuid(),
				application.getStudent() == null ? null : application.getStudent().getPublicUuid(), name,
				application.getAcademicYear().getPublicUuid(), application.getAcademicYear().getName(),
				application.getGrade().getPublicUuid(), application.getGrade().getName(), application.getStatus(),
				application.getSource(), application.getSubmittedAt(), application.getLastStatusNote(),
				application.getEnrollmentPublicUuid(), application.getCreatedAt(), application.getUpdatedAt(),
				application.getOfferStatus(), application.getOfferSentAt(),
				null,
				application.getOfferResponseAt());
	}

	private AdmissionInquiry findInquiry(UUID publicUuid) {
		return inquiryRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionInquiry", publicUuid));
	}

	private AdmissionApplication findApplication(UUID publicUuid) {
		return applicationRepository.findByPublicUuidForUpdate(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionApplication", publicUuid));
	}

	private AcademicYear findYear(UUID publicUuid) {
		return yearRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", publicUuid));
	}

	private void appendStatusEvent(AdmissionApplication application, AdmissionApplicationStatus from,
			AdmissionApplicationStatus to, String note) {
		AdmissionStatusEvent event = new AdmissionStatusEvent();
		event.setApplication(application);
		event.setFromStatus(from);
		event.setToStatus(to);
		event.setNote(note);
		event.setActorUserId(currentUserProvider.currentUserId().orElse(null));
		eventRepository.save(event);
	}

	private void appendInquiryEvent(AdmissionInquiry inquiry, AdmissionInquiryStatus from,
			AdmissionInquiryStatus to, String note, AdmissionInquiryEvent.ActorType actorType) {
		AdmissionInquiryEvent event = new AdmissionInquiryEvent();
		event.setInquiry(inquiry);
		event.setFromStatus(from);
		event.setToStatus(to);
		event.setNote(note);
		event.setActorType(actorType);
		event.setActorUserId(actorType == AdmissionInquiryEvent.ActorType.STAFF
				? currentUserProvider.currentUserId().orElse(null) : null);
		inquiryEventRepository.save(event);
	}

	private static boolean inquiryTransitionAllowed(AdmissionInquiryStatus from, AdmissionInquiryStatus to) {
		return switch (from) {
			case RECEIVED -> to == AdmissionInquiryStatus.CONTACTED || to == AdmissionInquiryStatus.QUALIFIED || to == AdmissionInquiryStatus.CLOSED;
			case CONTACTED -> to == AdmissionInquiryStatus.QUALIFIED || to == AdmissionInquiryStatus.CLOSED;
			case QUALIFIED -> to == AdmissionInquiryStatus.CONTACTED || to == AdmissionInquiryStatus.CLOSED;
			case CONVERTED, CLOSED -> false;
		};
	}

	private static String applicantName(AdmissionApplication application) {
		if (application.getStudent() != null) return application.getStudent().fullName();
		return String.join(" ", List.of(application.getStudentFirstName() == null ? "" : application.getStudentFirstName(),
				application.getStudentLastName() == null ? "" : application.getStudentLastName(),
				application.getStudentSecondLastName() == null ? "" : application.getStudentSecondLastName()))
				.trim().replaceAll("\\s+", " ");
	}

	private static String validatedPhone(String value) {
		String phone = value == null ? "" : value.trim();
		int digits = phone.replaceAll("\\D", "").length();
		if (!phone.matches("^[+0-9()\\-\\s]{7,32}$") || digits < 7 || digits > 15) {
			throw new BadRequestException("ADMISSION_CONTACT_INVALID", "Ingresa un teléfono válido de 7 a 15 dígitos.");
		}
		return phone;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
