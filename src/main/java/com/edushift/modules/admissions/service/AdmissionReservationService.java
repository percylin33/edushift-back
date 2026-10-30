package com.edushift.modules.admissions.service;

import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.admissions.dto.AdmissionReservationResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationAdvanceResponse;
import com.edushift.modules.admissions.dto.AdmissionReservationAdvanceEventResponse;
import com.edushift.modules.admissions.dto.CreateAdmissionReservationRequest;
import com.edushift.modules.admissions.dto.RecordAdmissionReservationAdvanceRequest;
import com.edushift.modules.admissions.dto.RecordAdmissionReservationAdvanceRefundRequest;
import com.edushift.modules.admissions.dto.UpdateAdmissionReservationRequest;
import com.edushift.modules.admissions.entity.AdmissionApplication;
import com.edushift.modules.admissions.entity.AdmissionApplicationStatus;
import com.edushift.modules.admissions.entity.AdmissionReservationStatus;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservation;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservationEvent;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservationAdvance;
import com.edushift.modules.admissions.entity.AdmissionVacancyReservationAdvanceEvent;
import com.edushift.modules.admissions.entity.AdmissionReservationAdvanceEventAction;
import com.edushift.modules.admissions.repository.AdmissionApplicationRepository;
import com.edushift.modules.admissions.repository.AdmissionVacancyReservationEventRepository;
import com.edushift.modules.admissions.repository.AdmissionVacancyReservationRepository;
import com.edushift.modules.admissions.repository.AdmissionVacancyReservationAdvanceRepository;
import com.edushift.modules.admissions.repository.AdmissionVacancyReservationAdvanceEventRepository;
import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdmissionReservationService {
	private final AdmissionApplicationRepository applicationRepository;
	private final AdmissionVacancyReservationRepository reservationRepository;
	private final AdmissionVacancyReservationEventRepository eventRepository;
	private final AdmissionVacancyReservationAdvanceRepository advanceRepository;
	private final AdmissionVacancyReservationAdvanceEventRepository advanceEventRepository;
	private final AcademicYearRepository yearRepository;
	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public AdmissionReservationAdvanceResponse recordAdvance(UUID reservationPublicUuid,
			RecordAdmissionReservationAdvanceRequest request) {
		AdmissionVacancyReservation reservation = reservationRepository.findByPublicUuid(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		AcademicYear year = lockYear(reservation.getAcademicYear().getPublicUuid());
		expireForYear(year);
		reservation = reservationRepository.findByPublicUuidForUpdate(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		if (reservation.getStatus() != AdmissionReservationStatus.TEMPORARY
				&& reservation.getStatus() != AdmissionReservationStatus.COMMITTED) {
			throw new ConflictException("ADMISSION_ADVANCE_NOT_ALLOWED",
					"Solo se puede registrar un anticipo mientras la vacante esté separada o comprometida.");
		}
		AdmissionVacancyReservationAdvance advance = new AdmissionVacancyReservationAdvance();
		advance.setTenantId(reservation.getTenantId());
		advance.setReservation(reservation);
		advance.setAmountCents(request.amountCents());
		advance.setAppliedCents(0);
		advance.setCurrency("PEN");
		advance.setMethod(request.method());
		advance.setReference(blankToNull(request.reference()));
		advance.setNotes(blankToNull(request.notes()));
		advance.setReceivedAt(Instant.now());
		advance = advanceRepository.saveAndFlush(advance);
		appendAdvanceEvent(advance, AdmissionReservationAdvanceEventAction.RECEIVED,
				advance.getAmountCents(), null, "Anticipo recibido al separar la vacante.");
		return toAdvanceResponse(advance);
	}

	@Transactional(readOnly = true)
	public List<AdmissionReservationAdvanceResponse> listAdvances(UUID reservationPublicUuid) {
		AdmissionVacancyReservation reservation = reservationRepository.findByPublicUuid(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		return advanceRepository.findAllByReservationOrderByReceivedAtAsc(reservation).stream()
				.map(this::toAdvanceResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<AdmissionReservationAdvanceEventResponse> listAdvanceEvents(UUID reservationPublicUuid) {
		reservationRepository.findByPublicUuid(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		return advanceEventRepository.findAllByAdvance_Reservation_PublicUuidOrderByCreatedAtAsc(reservationPublicUuid)
				.stream()
				.map(event -> new AdmissionReservationAdvanceEventResponse(
						event.getAdvance().getPublicUuid(), event.getAction(), event.getAmountCents(),
						event.getInvoicePublicUuid(), event.getNote(), event.getActorUserId(), event.getCreatedAt()))
				.toList();
	}

	@Transactional
	public AdmissionReservationAdvanceResponse recordAdvanceRefund(UUID reservationPublicUuid, UUID advancePublicUuid,
			RecordAdmissionReservationAdvanceRefundRequest request) {
		AdmissionVacancyReservation reservation = reservationRepository.findByPublicUuid(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		AcademicYear year = lockYear(reservation.getAcademicYear().getPublicUuid());
		expireForYear(year);
		reservation = reservationRepository.findByPublicUuidForUpdate(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		if (reservation.getStatus() != AdmissionReservationStatus.RELEASED
				&& reservation.getStatus() != AdmissionReservationStatus.EXPIRED) {
			throw new ConflictException("ADMISSION_ADVANCE_REFUND_NOT_ALLOWED",
					"Primero libera o deja vencer la vacante antes de registrar la devolución.");
		}
		AdmissionVacancyReservationAdvance advance = advanceRepository.findByPublicUuidForUpdate(advancePublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservationAdvance", advancePublicUuid));
		if (!advance.getReservation().getPublicUuid().equals(reservationPublicUuid)) {
			throw new ResourceNotFoundException("AdmissionVacancyReservationAdvance", advancePublicUuid);
		}
		if (advance.getStatus() != com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.AVAILABLE
				|| advance.getAppliedCents() != 0) {
			throw new ConflictException("ADMISSION_ADVANCE_REFUND_NOT_ALLOWED",
					"Solo se puede devolver el anticipo completo antes de aplicarlo al cargo.");
		}
		advance.setStatus(com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.REFUNDED);
		advance.setNotes(joinRefundNote(advance.getNotes(), request.reference(), request.notes()));
		advanceRepository.save(advance);
		String refundEventNote = "Devolución total registrada. Referencia: " + request.reference().trim();
		if (refundEventNote.length() > 500) refundEventNote = refundEventNote.substring(0, 500);
		appendAdvanceEvent(advance, AdmissionReservationAdvanceEventAction.REFUNDED, advance.getAmountCents(), null,
				refundEventNote);
		return toAdvanceResponse(advance);
	}

	/** Applies recorded reservation money to an enrollment invoice in the same transaction. */
	@Transactional
	public List<AppliedAdmissionAdvance> applyAvailableAdvances(UUID applicationPublicUuid, Invoice invoice) {
		AdmissionApplication application = applicationRepository.findByPublicUuid(applicationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionApplication", applicationPublicUuid));
		if (application.getStudent() == null || !application.getStudent().getPublicUuid().equals(invoice.getStudentId())
				|| (invoice.getAcademicYearPublicUuid() != null
						&& !application.getAcademicYear().getPublicUuid().equals(invoice.getAcademicYearPublicUuid()))) {
			throw new ConflictException("ADMISSION_ADVANCE_INVOICE_MISMATCH",
					"El anticipo no corresponde al estudiante o año del cargo.");
		}
		if (application.getStatus() != AdmissionApplicationStatus.ENROLLED) return List.of();
		if (invoice.getStatus() != Invoice.Status.PENDING && invoice.getStatus() != Invoice.Status.OVERDUE) {
			return List.of();
		}
		long remaining = invoice.getTotalCents() - invoice.getAdvanceAppliedCents();
		if (remaining <= 0) return List.of();
		List<AppliedAdmissionAdvance> applied = new java.util.ArrayList<>();
		for (AdmissionVacancyReservationAdvance advance : advanceRepository
				.findAvailableForApplicationForUpdate(applicationPublicUuid)) {
			if (remaining <= 0) break;
			if (!invoice.getCurrency().equalsIgnoreCase(advance.getCurrency())) continue;
			long amount = Math.min(remaining, advance.getAmountCents() - advance.getAppliedCents());
			if (amount <= 0) continue;
			advance.setAppliedCents(advance.getAppliedCents() + amount);
			advance.setStatus(advance.getAppliedCents() == advance.getAmountCents()
					? com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.APPLIED
					: com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.PARTIALLY_APPLIED);
			advanceRepository.save(advance);
			appendAdvanceEvent(advance, AdmissionReservationAdvanceEventAction.APPLIED, amount,
					invoice.getPublicUuid(), "Anticipo aplicado al cargo de matrícula.");
			applied.add(new AppliedAdmissionAdvance(advance.getPublicUuid(), amount, advance.getCurrency(),
					advance.getMethod(), advance.getReference(), advance.getReceivedAt()));
			remaining -= amount;
		}
		return List.copyOf(applied);
	}

	@Transactional
	public AdmissionReservationResponse reserve(UUID applicationPublicUuid, CreateAdmissionReservationRequest request) {
		AdmissionApplication application = applicationRepository.findByPublicUuidForUpdate(applicationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionApplication", applicationPublicUuid));
		AcademicYear year = lockYear(application.getAcademicYear().getPublicUuid());
		expireForYear(year);
		if (application.getStatus() == AdmissionApplicationStatus.ENROLLED
				|| application.getStatus() == AdmissionApplicationStatus.REJECTED
				|| application.getStatus() == AdmissionApplicationStatus.WITHDRAWN) {
			throw new ConflictException("ADMISSION_RESERVATION_NOT_ALLOWED", "Esta postulación no puede reservar una vacante.");
		}
		if (request.expiresAt() == null || !request.expiresAt().isAfter(Instant.now())) {
			throw new BadRequestException("ADMISSION_RESERVATION_EXPIRY_INVALID", "La fecha límite debe estar en el futuro.");
		}
		AdmissionVacancyReservation reservation = reservationRepository.findByApplication_Id(application.getId()).orElse(null);
		if (reservation != null && (reservation.getStatus() == AdmissionReservationStatus.TEMPORARY
				|| reservation.getStatus() == AdmissionReservationStatus.COMMITTED)
				&& (reservation.getStatus() == AdmissionReservationStatus.COMMITTED
						|| (reservation.getExpiresAt() != null && reservation.getExpiresAt().isAfter(Instant.now())))) {
			throw new ConflictException("ADMISSION_RESERVATION_EXISTS", "La postulación ya tiene una vacante reservada.");
		}
		ensureCapacity(application, year);
		AdmissionReservationStatus previous = reservation == null ? null : reservation.getStatus();
		if (reservation == null) {
			reservation = new AdmissionVacancyReservation();
			reservation.setTenantId(application.getTenantId());
			reservation.setApplication(application);
			reservation.setAcademicYear(year);
			reservation.setGrade(application.getGrade());
		}
		reservation.setStatus(AdmissionReservationStatus.TEMPORARY);
		reservation.setExpiresAt(request.expiresAt());
		reservation.setReason(blankToNull(request.reason()));
		reservation = reservationRepository.saveAndFlush(reservation);
		appendEvent(reservation, previous, AdmissionReservationStatus.TEMPORARY,
				blankToNull(request.reason()) == null ? "Separación temporal de vacante." : request.reason().trim());
		return toResponse(reservation);
	}

	@Transactional
	public AdmissionReservationResponse update(UUID reservationPublicUuid, UpdateAdmissionReservationRequest request) {
		AdmissionVacancyReservation reservation = reservationRepository.findByPublicUuid(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		lockYear(reservation.getAcademicYear().getPublicUuid());
		expireForYear(reservation.getAcademicYear());
		reservation = reservationRepository.findByPublicUuidForUpdate(reservationPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AdmissionVacancyReservation", reservationPublicUuid));
		if (request.status() != AdmissionReservationStatus.RELEASED) {
			throw new BadRequestException("ADMISSION_RESERVATION_TRANSITION_INVALID", "Solo se puede liberar una separación desde esta acción.");
		}
		if (reservation.getStatus() == AdmissionReservationStatus.CONSUMED
				|| reservation.getStatus() == AdmissionReservationStatus.EXPIRED
				|| reservation.getStatus() == AdmissionReservationStatus.RELEASED) {
			return toResponse(reservation);
		}
		AdmissionReservationStatus previous = reservation.getStatus();
		reservation.setStatus(AdmissionReservationStatus.RELEASED);
		reservation.setExpiresAt(null);
		reservation.setReason(blankToNull(request.reason()));
		reservationRepository.save(reservation);
		appendEvent(reservation, previous, AdmissionReservationStatus.RELEASED,
				blankToNull(request.reason()) == null ? "Vacante liberada por el colegio." : request.reason().trim());
		return toResponse(reservation);
	}

	@Transactional
	public List<AdmissionReservationResponse> list(AcademicYear year) {
		lockYear(year.getPublicUuid());
		expireForYear(year);
		return reservationRepository.findAllByYear(year).stream().map(this::toResponse).toList();
	}

	@Transactional
	public void commitOnAdmission(AdmissionApplication application) {
		AcademicYear year = lockYear(application.getAcademicYear().getPublicUuid());
		expireForYear(year);
		AdmissionVacancyReservation reservation = reservationRepository.findByApplication_Id(application.getId()).orElse(null);
		if (reservation != null && reservation.getStatus() == AdmissionReservationStatus.COMMITTED) return;
		ensureCapacity(application, year);
		AdmissionReservationStatus previous = reservation == null ? null : reservation.getStatus();
		if (reservation == null) {
			reservation = new AdmissionVacancyReservation();
			reservation.setTenantId(application.getTenantId());
			reservation.setApplication(application);
			reservation.setAcademicYear(year);
			reservation.setGrade(application.getGrade());
		}
		reservation.setStatus(AdmissionReservationStatus.COMMITTED);
		reservation.setExpiresAt(null);
		reservation.setReason("Vacante comprometida al admitir la postulación.");
		reservation = reservationRepository.saveAndFlush(reservation);
		appendEvent(reservation, previous, AdmissionReservationStatus.COMMITTED, reservation.getReason());
	}

	@Transactional
	public void consumeOnEnrollment(AdmissionApplication application) {
		AdmissionVacancyReservation reservation = reservationRepository.findByApplication_Id(application.getId()).orElse(null);
		if (reservation == null) return;
		lockYear(reservation.getAcademicYear().getPublicUuid());
		reservation = reservationRepository.findByPublicUuidForUpdate(reservation.getPublicUuid()).orElse(null);
		if (reservation == null || reservation.getStatus() == AdmissionReservationStatus.CONSUMED) return;
		AdmissionReservationStatus previous = reservation.getStatus();
		reservation.setStatus(AdmissionReservationStatus.CONSUMED);
		reservation.setExpiresAt(null);
		reservationRepository.save(reservation);
		appendEvent(reservation, previous, AdmissionReservationStatus.CONSUMED, "Vacante consumida al completar la matrícula.");
	}

	@Transactional
	public void releaseOnWithdrawal(AdmissionApplication application, String reason) {
		AdmissionVacancyReservation reservation = reservationRepository.findByApplication_Id(application.getId()).orElse(null);
		if (reservation == null) return;
		lockYear(reservation.getAcademicYear().getPublicUuid());
		reservation = reservationRepository.findByPublicUuidForUpdate(reservation.getPublicUuid()).orElse(null);
		if (reservation == null || (reservation.getStatus() != AdmissionReservationStatus.TEMPORARY
				&& reservation.getStatus() != AdmissionReservationStatus.COMMITTED)) return;
		AdmissionReservationStatus previous = reservation.getStatus();
		reservation.setStatus(AdmissionReservationStatus.RELEASED);
		reservation.setExpiresAt(null);
		reservation.setReason(blankToNull(reason));
		reservationRepository.save(reservation);
		appendEvent(reservation, previous, AdmissionReservationStatus.RELEASED,
				blankToNull(reason) == null ? "Vacante liberada al retirar la postulación." : reason.trim());
	}

	public long effectiveCount(AcademicYear year, Grade grade) {
		return reservationRepository.countEffective(year, grade, Instant.now());
	}

	public long temporaryCount(AcademicYear year, Grade grade) {
		return reservationRepository.countTemporary(year, grade, Instant.now());
	}

	public long committedCount(AcademicYear year, Grade grade) {
		return reservationRepository.countEffective(year, grade, Instant.now()) - temporaryCount(year, grade);
	}

	private void ensureCapacity(AdmissionApplication application, AcademicYear year) {
		List<Section> sections = sectionRepository.findAllByAcademicYearAndGradeOrderByDisplayOrderAscNameAsc(year, application.getGrade());
		if (sections.isEmpty() || sections.stream().anyMatch(section -> section.getCapacity() == null)) {
			throw new ConflictException("ADMISSION_CAPACITY_NOT_CONFIGURED", "Configura el aforo de las secciones antes de reservar.");
		}
		int capacity = sections.stream().mapToInt(Section::getCapacity).sum();
		long occupied = enrollmentRepository.countActiveByYearAndGrade(year, application.getGrade());
		long reserved = reservationRepository.countEffectiveExcludingApplication(year, application.getGrade(),
				application.getId(), Instant.now());
		if (occupied + reserved >= capacity) {
			throw new ConflictException("ADMISSION_NO_VACANCY", "No quedan vacantes disponibles para este grado.");
		}
	}

	private void expireForYear(AcademicYear year) {
		Instant now = Instant.now();
		for (AdmissionVacancyReservation reservation : reservationRepository.findExpiredByYear(year, now)) {
			AdmissionReservationStatus previous = reservation.getStatus();
			reservation.setStatus(AdmissionReservationStatus.EXPIRED);
			reservation.setExpiresAt(null);
			reservationRepository.save(reservation);
			appendEvent(reservation, previous, AdmissionReservationStatus.EXPIRED,
					"La separación temporal venció y liberó la vacante.");
		}
	}

	private AcademicYear lockYear(UUID publicUuid) {
		return yearRepository.findByPublicUuidForUpdate(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", publicUuid));
	}

	private void appendEvent(AdmissionVacancyReservation reservation, AdmissionReservationStatus from,
			AdmissionReservationStatus to, String note) {
		AdmissionVacancyReservationEvent event = new AdmissionVacancyReservationEvent();
		event.setTenantId(reservation.getTenantId());
		event.setReservation(reservation);
		event.setFromStatus(from);
		event.setToStatus(to);
		event.setNote(note);
		event.setActorUserId(currentUserProvider.currentUserId().orElse(null));
		eventRepository.save(event);
	}

	private AdmissionReservationResponse toResponse(AdmissionVacancyReservation reservation) {
		AdmissionApplication application = reservation.getApplication();
		String name = application.getStudent() == null
				? String.join(" ", java.util.stream.Stream.of(application.getStudentFirstName(), application.getStudentLastName(), application.getStudentSecondLastName())
						.filter(value -> value != null && !value.isBlank()).toList())
				: application.getStudent().fullName();
		return new AdmissionReservationResponse(reservation.getPublicUuid(), application.getPublicUuid(), name,
				reservation.getAcademicYear().getPublicUuid(), reservation.getAcademicYear().getName(),
				reservation.getGrade().getPublicUuid(), reservation.getGrade().getName(), reservation.getStatus(),
				reservation.getExpiresAt(), reservation.getReason(),
				application.getStudent() == null ? null : application.getStudent().getPublicUuid(),
				advanceRepository.sumReceivedByReservation(reservation),
				advanceRepository.sumAvailableByReservation(reservation));
	}

	private AdmissionReservationAdvanceResponse toAdvanceResponse(AdmissionVacancyReservationAdvance advance) {
		return new AdmissionReservationAdvanceResponse(advance.getPublicUuid(),
				advance.getReservation().getPublicUuid(), advance.getAmountCents(), advance.getAppliedCents(),
				advance.getStatus() == com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus.REFUNDED
						? 0 : advance.getAmountCents() - advance.getAppliedCents(), advance.getCurrency(), advance.getMethod(),
				advance.getStatus(), advance.getReference(), advance.getNotes(), advance.getReceivedAt());
	}

	private String joinRefundNote(String existing, String reference, String notes) {
		String refund = "Devolución registrada: " + reference.trim()
				+ (blankToNull(notes) == null ? "" : " · " + notes.trim());
		String combined = blankToNull(existing) == null ? refund : existing.trim() + " · " + refund;
		return combined.length() <= 500 ? combined : combined.substring(0, 500);
	}

	private void appendAdvanceEvent(AdmissionVacancyReservationAdvance advance,
			AdmissionReservationAdvanceEventAction action, long amountCents, UUID invoicePublicUuid, String note) {
		AdmissionVacancyReservationAdvanceEvent event = new AdmissionVacancyReservationAdvanceEvent();
		event.setTenantId(advance.getTenantId());
		event.setAdvance(advance);
		event.setAction(action);
		event.setAmountCents(amountCents);
		event.setInvoicePublicUuid(invoicePublicUuid);
		event.setNote(note);
		event.setActorUserId(currentUserProvider.currentUserId().orElse(null));
		advanceEventRepository.save(event);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
