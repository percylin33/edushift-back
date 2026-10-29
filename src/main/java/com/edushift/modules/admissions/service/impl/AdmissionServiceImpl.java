package com.edushift.modules.admissions.service.impl;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.admissions.dto.AdmissionApplicationResponse;
import com.edushift.modules.admissions.dto.AdmissionApplicationDetailResponse;
import com.edushift.modules.admissions.dto.AdmissionApplicationDetailResponse.StudentDetails;
import com.edushift.modules.admissions.dto.AdmissionApplicationDetailResponse.GuardianDetails;
import com.edushift.modules.admissions.dto.AdmissionApplicationDetailResponse.InquiryDetails;
import com.edushift.modules.admissions.dto.AdmissionInquiryEventResponse;
import com.edushift.modules.admissions.repository.AdmissionInquiryEventRepository;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.modules.admissions.dto.AdmissionInquiryResponse;
import com.edushift.modules.admissions.dto.AdmissionStatusEventResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationEventResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionReservationRequest;
import com.edushift.modules.admissions.dto.UpdateAdmissionReservationRequest;
import com.edushift.modules.admissions.dto.CreateAdmissionApplicationRequest;
import com.edushift.modules.admissions.dto.GradeVacancyResponse;
import com.edushift.modules.admissions.dto.LinkAdmissionEnrollmentRequest;
import com.edushift.modules.admissions.dto.TransitionAdmissionStatusRequest;
import com.edushift.modules.admissions.dto.CreateAdmissionFromInquiryRequest;
import com.edushift.modules.admissions.dto.OfferLinkResponse;
import com.edushift.modules.admissions.dto.UpdateAdmissionInquiryRequest;
import com.edushift.modules.admissions.dto.CompleteAdmissionEnrollmentRequest;
import com.edushift.modules.admissions.entity.AdmissionApplication;
import com.edushift.modules.admissions.entity.AdmissionApplicationStatus;
import com.edushift.modules.admissions.entity.AdmissionStatusEvent;
import com.edushift.modules.admissions.repository.AdmissionApplicationRepository;
import com.edushift.modules.admissions.repository.AdmissionStatusEventRepository;
import com.edushift.modules.admissions.repository.AdmissionVacancyReservationRepository;
import com.edushift.modules.admissions.repository.AdmissionVacancyReservationEventRepository;
import com.edushift.modules.admissions.service.AdmissionReservationService;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservation;
import com.edushift.modules.admissions.service.AdmissionService;
import com.edushift.modules.admissions.service.AdmissionWorkflowService;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdmissionServiceImpl implements AdmissionService {

	private final com.edushift.modules.admissions.mapper.AdmissionApplicantMapper applicantMapper;
	private final AdmissionApplicationRepository applicationRepository;
	private final AdmissionStatusEventRepository eventRepository;
	private final AcademicYearRepository yearRepository;
	private final GradeRepository gradeRepository;
	private final SectionRepository sectionRepository;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final CurrentUserProvider currentUserProvider;
	private final AdmissionWorkflowService workflowService;
	private final AdmissionInquiryEventRepository inquiryEventRepository;
	private final StudentGuardianService guardianService;
	private final AdmissionReservationService reservationService;
	private final AdmissionVacancyReservationRepository reservationRepository;
	private final AdmissionVacancyReservationEventRepository reservationEventRepository;

	@Override
	@Transactional(readOnly = true)
	public AdmissionApplicationDetailResponse detail(UUID applicationPublicUuid) {
		AdmissionApplication application = applicationRepository.findByPublicUuid(applicationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionApplication", applicationPublicUuid));
		Student student = application.getStudent();
		boolean formal = application.getStudentDocumentNumber() != null;
		StudentDetails studentDetails = formal ? new StudentDetails(
				fullName(application.getStudentFirstName(), application.getStudentLastName(), application.getStudentSecondLastName()),
				application.getStudentDocumentType(), application.getStudentDocumentNumber(),
				application.getStudentBirthDate(), application.getStudentGender())
				: student == null ? new StudentDetails(null, null, null, null, null)
				: new StudentDetails(student.fullName(), student.getDocumentType(), student.getDocumentNumber(), student.getBirthDate(), student.getGender());
		List<GuardianDetails> guardians;
		if (application.getGuardianDocumentNumber() != null) {
			guardians = List.of(new GuardianDetails(fullName(application.getGuardianFirstName(), application.getGuardianLastName()),
					application.getGuardianDocumentType(), application.getGuardianDocumentNumber(), application.getGuardianEmail(),
					application.getGuardianPhone(), application.getGuardianRelationship()));
		} else {
			guardians = student == null ? List.of() : guardianService.listGuardians(student.getPublicUuid()).stream()
					.map(guardian -> new GuardianDetails(guardian.fullName(), guardian.documentType(), guardian.documentNumber(),
							guardian.email(), guardian.phone(), guardian.relationship())).toList();
		}
		var inquiry = application.getInquiry();
		InquiryDetails inquiryDetails = inquiry == null ? null : new InquiryDetails(inquiry.getPublicUuid(), inquiry.getContactName(),
				inquiry.getContactValue(), inquiry.getAcademicYear().getName(), inquiry.getGrade().getName(), inquiry.getCreatedAt(), inquiry.getNote(),
				inquiryEventRepository.findAllByInquiryOrderByCreatedAtAsc(inquiry).stream()
						.map(event -> new AdmissionInquiryEventResponse(event.getFromStatus(), event.getToStatus(), event.getNote(),
								event.getActorUserId(), event.getActorType(), event.getCreatedAt())).toList());
		var history = eventRepository.findAllByApplicationOrderByCreatedAtAsc(application).stream()
				.map(event -> new AdmissionStatusEventResponse(event.getFromStatus(), event.getToStatus(), event.getNote(),
					event.getActorUserId(), event.getCreatedAt())).toList();
		return new AdmissionApplicationDetailResponse(toResponse(application), studentDetails, guardians, inquiryDetails, history, applicantMapper.toRequest(application));
	}

    @Override
    @Transactional
    public AdmissionApplicationResponse updateApplicant(UUID publicUuid,
            com.edushift.modules.admissions.dto.UpdateAdmissionApplicantRequest request) {
        AdmissionApplication application = findApplication(publicUuid);
        AcademicYear year = lockYear(application.getAcademicYear().getPublicUuid());
        if (year.getStatus() == com.edushift.modules.academic.year.entity.AcademicYearStatus.CLOSED) {
            throw new ConflictException("ADMISSION_YEAR_CLOSED", "El año académico está cerrado.");
        }
        if (application.getStudent() != null || application.getStudentDocumentNumber() == null
                || !java.util.Set.of(AdmissionApplicationStatus.RECEIVED, AdmissionApplicationStatus.UNDER_REVIEW,
                        AdmissionApplicationStatus.WAITLISTED).contains(application.getStatus())) {
            throw new ConflictException("ADMISSION_EDIT_NOT_ALLOWED", "Solo se pueden corregir expedientes abiertos sin ficha vinculada.");
        }
        var applicant = request.applicant();
        if ((applicant.academicYearPublicUuid() != null && !applicant.academicYearPublicUuid().equals(year.getPublicUuid()))
                || (applicant.gradePublicUuid() != null && !applicant.gradePublicUuid().equals(application.getGrade().getPublicUuid()))) {
            throw new ConflictException("ADMISSION_EDIT_NOT_ALLOWED", "La corrección no permite cambiar el año o grado de destino.");
        }
        String document = applicant.studentDocumentNumber().trim().toUpperCase(java.util.Locale.ROOT);
        boolean documentChanged = applicant.studentDocumentType() != application.getStudentDocumentType()
                || !document.equals(application.getStudentDocumentNumber());
        if (documentChanged && applicationRepository.existsByAcademicYearAndStudentDocumentTypeAndStudentDocumentNumber(
                year, applicant.studentDocumentType(), document)) {
            throw new ConflictException("ADMISSION_APPLICANT_ALREADY_EXISTS", "Ya existe una solicitud con ese documento para el año.");
        }
        applicantMapper.update(application, applicant);
        applicationRepository.saveAndFlush(application);
        // Record the correction and its reason without copying identity values into the event.
        appendEvent(application, application.getStatus(), application.getStatus(), "Corrección de datos del expediente: " + request.reason().trim());
        return toResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.edushift.modules.admissions.dto.AdmissionSectionVacancyResponse> sectionVacancies(UUID yearPublicUuid, UUID gradePublicUuid) {
        AcademicYear year = findYear(yearPublicUuid);
        Grade grade = gradeRepository.findByPublicUuid(gradePublicUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Grade", gradePublicUuid));
        return sectionRepository.findAllByAcademicYearAndGradeOrderByDisplayOrderAscNameAsc(year, grade).stream().map(section -> {
            long occupied = enrollmentRepository.countActiveBySection(section);
            Integer available = section.getCapacity() == null ? null : (int) Math.max(0, section.getCapacity() - occupied);
            return new com.edushift.modules.admissions.dto.AdmissionSectionVacancyResponse(section.getPublicUuid(), section.getName(), section.getCapacity(), occupied, available);
        }).toList();
    }

	private String fullName(String... names) {
		return java.util.Arrays.stream(names).filter(value -> value != null && !value.isBlank())
				.collect(java.util.stream.Collectors.joining(" "));
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdmissionApplicationResponse> listApplications(UUID academicYearPublicUuid) {
		AcademicYear year = findYear(academicYearPublicUuid);
		return applicationRepository.findAllForYear(year)
				.stream().map(this::toResponse).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<GradeVacancyResponse> listVacancies(UUID academicYearPublicUuid) {
		AcademicYear year = findYear(academicYearPublicUuid);
		Map<UUID, List<Section>> sectionsByGrade = new LinkedHashMap<>();
		for (Section section : sectionRepository.findAllByAcademicYearOrderByDisplayOrderAscNameAsc(year)) {
			sectionsByGrade.computeIfAbsent(section.getGrade().getPublicUuid(), ignored -> new ArrayList<>()).add(section);
		}
		return sectionsByGrade.values().stream().map(sections -> vacancyFor(year, sections.get(0).getGrade(), sections)).toList();
	}

	@Override
	@Transactional
	public List<AdmissionReservationResponse> listReservations(UUID academicYearPublicUuid) {
		return reservationService.list(findYear(academicYearPublicUuid));
	}

	@Override
	@Transactional
	public AdmissionReservationResponse reserve(UUID applicationPublicUuid, CreateAdmissionReservationRequest request) {
		return reservationService.reserve(applicationPublicUuid, request);
	}

	@Override
	@Transactional
	public AdmissionReservationResponse updateReservation(UUID reservationPublicUuid,
			UpdateAdmissionReservationRequest request) {
		return reservationService.update(reservationPublicUuid, request);
	}

	@Override
	@Transactional
	public com.edushift.modules.admissions.dto.AdmissionReservationAdvanceResponse recordReservationAdvance(
			UUID reservationPublicUuid,
			com.edushift.modules.admissions.dto.RecordAdmissionReservationAdvanceRequest request) {
		return reservationService.recordAdvance(reservationPublicUuid, request);
	}

	@Override
	@Transactional(readOnly = true)
	public List<com.edushift.modules.admissions.dto.AdmissionReservationAdvanceResponse> reservationAdvances(
			UUID reservationPublicUuid) {
		return reservationService.listAdvances(reservationPublicUuid);
	}

	@Override
	@Transactional(readOnly = true)
	public List<com.edushift.modules.admissions.dto.AdmissionReservationAdvanceEventResponse> reservationAdvanceEvents(
			UUID reservationPublicUuid) {
		return reservationService.listAdvanceEvents(reservationPublicUuid);
	}

	@Override
	@Transactional
	public com.edushift.modules.admissions.dto.AdmissionReservationAdvanceResponse refundReservationAdvance(
			UUID reservationPublicUuid, UUID advancePublicUuid,
			com.edushift.modules.admissions.dto.RecordAdmissionReservationAdvanceRefundRequest request) {
		return reservationService.recordAdvanceRefund(reservationPublicUuid, advancePublicUuid, request);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdmissionReservationEventResponse> reservationHistory(UUID reservationPublicUuid) {
		AdmissionVacancyReservation reservation = reservationRepository.findByPublicUuid(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		return reservationEventRepository.findAllByReservationOrderByCreatedAtAsc(reservation).stream()
				.map(event -> new AdmissionReservationEventResponse(event.getFromStatus(), event.getToStatus(),
						event.getNote(), event.getActorUserId(), event.getCreatedAt()))
				.toList();
	}

	@Override
	@Transactional
	public AdmissionApplicationResponse create(UUID academicYearPublicUuid, CreateAdmissionApplicationRequest request) {
		AcademicYear year = lockYear(academicYearPublicUuid);
        requireOpenYear(year);
		Student student = studentRepository.findByPublicUuid(request.studentPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("Student", request.studentPublicUuid()));
		Grade grade = gradeRepository.findByPublicUuid(request.gradePublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("Grade", request.gradePublicUuid()));

		if (enrollmentRepository.findActiveByStudentAndYear(student, year).isPresent()) {
			throw new ConflictException("STUDENT_ALREADY_ENROLLED_FOR_YEAR",
					"El estudiante ya cuenta con una matrícula activa en este año académico.");
		}
		if (applicationRepository.existsByAcademicYearAndStudent(year, student)) {
			throw new ConflictException("ADMISSION_APPLICATION_EXISTS",
					"Ya existe una postulación de este estudiante para el año académico seleccionado.");
		}

		AdmissionApplication application = new AdmissionApplication();
		application.setAcademicYear(year);
		application.setStudent(student);
		application.setGrade(grade);
		application.setSource(request.source());
		application.setStatus(AdmissionApplicationStatus.RECEIVED);
		application.setLastStatusNote(blankToNull(request.note()));
		try {
			application = applicationRepository.saveAndFlush(application);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("ADMISSION_APPLICATION_EXISTS",
					"Ya existe una postulación de este estudiante para el año académico seleccionado.", ex);
		}
		appendEvent(application, null, AdmissionApplicationStatus.RECEIVED, application.getLastStatusNote());
		return toResponse(application);
	}

	@Override
	@Transactional
	public AdmissionApplicationResponse transition(UUID applicationPublicUuid,
			TransitionAdmissionStatusRequest request) {
		AdmissionApplication application = findApplication(applicationPublicUuid);
		requireOpenYear(lockYear(application.getAcademicYear().getPublicUuid()));
		AdmissionApplicationStatus current = application.getStatus();
		AdmissionApplicationStatus target = request.status();
		if (current == target) return toResponse(application);
		if (!isAllowed(current, target)) {
			throw new ConflictException("ADMISSION_INVALID_TRANSITION",
					"No se puede cambiar una postulación de " + current + " a " + target + ".");
		}

		String note = blankToNull(request.note());
		if (requiresReason(target) && note == null) {
			throw new BadRequestException("ADMISSION_REASON_REQUIRED",
					"Registra el motivo para devolver, poner en lista de espera o rechazar la postulación.");
		}
		if (target == AdmissionApplicationStatus.ADMITTED) reservationService.commitOnAdmission(application);

		application.setStatus(target);
		application.setLastStatusNote(note);
		if (target == AdmissionApplicationStatus.WITHDRAWN
				&& application.getOfferStatus() != com.edushift.modules.admissions.entity.AdmissionOfferStatus.NOT_REQUIRED) {
			application.setOfferStatus(com.edushift.modules.admissions.entity.AdmissionOfferStatus.RELEASED);
			application.setOfferTokenHash(null);
		}
		applicationRepository.save(application);
		appendEvent(application, current, target, note);
		if (target == AdmissionApplicationStatus.WITHDRAWN) reservationService.releaseOnWithdrawal(application, note);
		return toResponse(application);
	}

	@Override
	@Transactional
	public AdmissionApplicationResponse linkEnrollment(UUID applicationPublicUuid,
			LinkAdmissionEnrollmentRequest request) {
		AdmissionApplication application = findApplication(applicationPublicUuid);
		lockYear(application.getAcademicYear().getPublicUuid());
		if (application.getStatus() == AdmissionApplicationStatus.ENROLLED
				&& request.enrollmentPublicUuid().equals(application.getEnrollmentPublicUuid())) {
			return toResponse(application);
		}
		if (application.getStatus() != AdmissionApplicationStatus.ADMITTED) {
			throw new ConflictException("ADMISSION_NOT_READY_TO_ENROLL",
					"La postulación debe estar admitida antes de vincular una matrícula.");
		}
		var enrollment = enrollmentRepository.findByPublicUuid(request.enrollmentPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("StudentEnrollment", request.enrollmentPublicUuid()));
		if (enrollment.getStatus() != StudentEnrollmentStatus.ACTIVE
				|| application.getStudent() == null
				|| !enrollment.getStudent().getId().equals(application.getStudent().getId())
				|| !enrollment.getAcademicYear().getId().equals(application.getAcademicYear().getId())
				|| !enrollment.getSection().getGrade().getId().equals(application.getGrade().getId())) {
			throw new ConflictException("ADMISSION_ENROLLMENT_MISMATCH",
					"La matrícula debe estar activa y corresponder al estudiante, año y grado de esta postulación.");
		}

		application.setEnrollmentPublicUuid(enrollment.getPublicUuid());
		application.setStatus(AdmissionApplicationStatus.ENROLLED);
		application.getStudent().setEnrollmentStatus(EnrollmentStatus.ENROLLED);
		if (application.getStudent().getEnrollmentDate() == null) {
			application.getStudent().setEnrollmentDate(enrollment.getEnrolledAt());
		}
		studentRepository.save(application.getStudent());
		String note = blankToNull(request.note());
		application.setLastStatusNote(note);
		applicationRepository.save(application);
		appendEvent(application, AdmissionApplicationStatus.ADMITTED, AdmissionApplicationStatus.ENROLLED, note);
		reservationService.consumeOnEnrollment(application);
		return toResponse(application);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdmissionStatusEventResponse> history(UUID applicationPublicUuid) {
		AdmissionApplication application = findApplication(applicationPublicUuid);
		return eventRepository.findAllByApplicationOrderByCreatedAtAsc(application).stream()
				.map(event -> new AdmissionStatusEventResponse(event.getFromStatus(), event.getToStatus(),
						event.getNote(), event.getActorUserId(), event.getCreatedAt()))
				.toList();
	}

	private GradeVacancyResponse vacancyFor(AcademicYear year, Grade grade, List<Section> sections) {
		boolean configured = !sections.isEmpty() && sections.stream().allMatch(section -> section.getCapacity() != null);
		int capacity = sections.stream().mapToInt(section -> section.getCapacity() == null ? 0 : section.getCapacity()).sum();
		long occupied = enrollmentRepository.countActiveByYearAndGrade(year, grade);
		long admitted = reservationService.committedCount(year, grade);
		long temporary = reservationService.temporaryCount(year, grade);
		int available = configured ? (int) Math.max(0, capacity - occupied - admitted - temporary) : 0;
		return new GradeVacancyResponse(grade.getPublicUuid(), grade.getName(), sections.size(), capacity,
				occupied, admitted, temporary, available, configured);
	}

	private void appendEvent(AdmissionApplication application, AdmissionApplicationStatus from,
			AdmissionApplicationStatus to, String note) {
		AdmissionStatusEvent event = new AdmissionStatusEvent();
		event.setApplication(application);
		event.setFromStatus(from);
		event.setToStatus(to);
		event.setNote(note);
		event.setActorUserId(currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Usuario no autenticado")));
		eventRepository.save(event);
	}

	private boolean isAllowed(AdmissionApplicationStatus from, AdmissionApplicationStatus to) {
		return switch (from) {
			case RECEIVED -> to == AdmissionApplicationStatus.UNDER_REVIEW || to == AdmissionApplicationStatus.WITHDRAWN;
			case UNDER_REVIEW -> to == AdmissionApplicationStatus.ADMITTED
					|| to == AdmissionApplicationStatus.WAITLISTED || to == AdmissionApplicationStatus.REJECTED
					|| to == AdmissionApplicationStatus.WITHDRAWN;
			case WAITLISTED -> to == AdmissionApplicationStatus.UNDER_REVIEW
					|| to == AdmissionApplicationStatus.ADMITTED || to == AdmissionApplicationStatus.REJECTED
					|| to == AdmissionApplicationStatus.WITHDRAWN;
			case ADMITTED -> to == AdmissionApplicationStatus.WITHDRAWN;
			case REJECTED, WITHDRAWN, ENROLLED -> false;
		};
	}

	private boolean requiresReason(AdmissionApplicationStatus status) {
		return status == AdmissionApplicationStatus.WAITLISTED
				|| status == AdmissionApplicationStatus.REJECTED
				|| status == AdmissionApplicationStatus.WITHDRAWN;
	}

	private AdmissionApplicationResponse toResponse(AdmissionApplication application) {
		Student student = application.getStudent();
		String fullName = student == null
				? String.join(" ", java.util.stream.Stream.of(application.getStudentFirstName(), application.getStudentLastName(), application.getStudentSecondLastName())
						.filter(value -> value != null && !value.isBlank()).toList())
				: student.fullName();
		return new AdmissionApplicationResponse(application.getPublicUuid(), student == null ? null : student.getPublicUuid(), fullName,
				application.getAcademicYear().getPublicUuid(), application.getAcademicYear().getName(),
				application.getGrade().getPublicUuid(), application.getGrade().getName(), application.getStatus(),
				application.getSource(), application.getSubmittedAt(), application.getLastStatusNote(),
				application.getEnrollmentPublicUuid(), application.getCreatedAt(), application.getUpdatedAt(),
				application.getOfferStatus(), application.getOfferSentAt(),
				null,
				application.getOfferResponseAt());
	}

	private AdmissionApplication findApplication(UUID publicUuid) {
		return applicationRepository.findByPublicUuidForUpdate(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionApplication", publicUuid));
	}

	private AcademicYear findYear(UUID publicUuid) {
		return yearRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", publicUuid));
	}

	private AcademicYear lockYear(UUID publicUuid) {
		return yearRepository.findByPublicUuidForUpdate(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", publicUuid));
	}

    private void requireOpenYear(AcademicYear year) {
        if (year.getStatus() == com.edushift.modules.academic.year.entity.AcademicYearStatus.CLOSED) {
            throw new ConflictException("ADMISSION_YEAR_CLOSED", "El año académico está cerrado.");
        }
    }

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
