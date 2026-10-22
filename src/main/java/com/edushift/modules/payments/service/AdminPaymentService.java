package com.edushift.modules.payments.service;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.event.NotificationEvent;
import com.edushift.modules.payments.dto.AdminPaymentFilter;
import com.edushift.modules.payments.dto.BulkEmitInvoicesRequest;
import com.edushift.modules.payments.dto.BulkEmitInvoicesResponse;
import com.edushift.modules.payments.dto.EmitInvoiceRequest;
import com.edushift.modules.payments.dto.InvoiceItemResponse;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.payments.dto.InvoiceReviewQueueItemResponse;
import com.edushift.modules.payments.dto.PaymentResponse;
import com.edushift.modules.payments.dto.RejectPaymentEvidenceRequest;
import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.InvoiceItem;
import com.edushift.modules.payments.entity.Payment;
import com.edushift.modules.payments.entity.PaymentConcept;
import com.edushift.modules.payments.exception.InvoiceNotFoundException;
import com.edushift.modules.payments.exception.PaymentConceptNotFoundException;
import com.edushift.modules.payments.repository.InvoiceItemRepository;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.payments.repository.PaymentConceptRepository;
import com.edushift.modules.payments.repository.PaymentRepository;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin payment operations (emit, voucher review, cash/refund).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

	private static final int BULK_MAX = 500;

	private final InvoiceRepository invoiceRepo;
	private final InvoiceItemRepository itemRepo;
	private final PaymentRepository paymentRepo;
	private final PaymentConceptRepository conceptRepo;
	private final FileObjectService fileObjectService;
	private final UserRepository userRepository;
	private final StudentRepository studentRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final SectionRepository sectionRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Autowired(required = false)
	private MercadoPagoClient mercadoPagoClient;

	private static final Set<String> ADMIN_LIST_SORTS = Set.of("createdAt", "paidAt", "amountCents");

	/**
	 * Tenant-scoped payment ledger (DEBT-11-PAY-1).
	 * Tenant comes only from {@link TenantContext} — never from the caller.
	 */
	@Transactional(readOnly = true)
	public Page<PaymentResponse> adminList(AdminPaymentFilter filter, Pageable pageable) {
		UUID tenantId = TenantContext.currentRequired();
		Pageable safe = sanitizeAdminListPageable(pageable);
		AdminPaymentFilter f = filter != null ? filter : AdminPaymentFilter.of(null, null, null);
		return paymentRepo
				.adminList(tenantId, f.status(), f.provider(), f.search(), safe)
				.map(PaymentResponse::from);
	}

	private static Pageable sanitizeAdminListPageable(Pageable pageable) {
		int page = Math.max(pageable.getPageNumber(), 0);
		int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
		Sort sort = pageable.getSort();
		if (sort == null || sort.isUnsorted()) {
			sort = Sort.by(Sort.Direction.DESC, "createdAt");
		} else {
			List<Sort.Order> orders = new ArrayList<>();
			for (Sort.Order order : sort) {
				if (ADMIN_LIST_SORTS.contains(order.getProperty())) {
					orders.add(order);
				}
			}
			sort = orders.isEmpty()
					? Sort.by(Sort.Direction.DESC, "createdAt")
					: Sort.by(orders);
		}
		return PageRequest.of(page, size, sort);
	}

	@Transactional(readOnly = true)
	public Page<InvoiceResponse> listInvoices(Invoice.Status status, Pageable pageable) {
		TenantContext.currentRequired();
		Page<Invoice> page = status == null
				? invoiceRepo.findAllByOrderByIssuedAtDesc(pageable)
				: invoiceRepo.findByStatusOrderByIssuedAtDesc(status, pageable);
		List<UUID> studentUuids = page.getContent().stream()
				.map(Invoice::getStudentId).distinct().toList();
		Map<UUID, Student> studentsByUuid = new HashMap<>();
		studentRepository.findAllByPublicUuidIn(studentUuids)
				.forEach(student -> studentsByUuid.put(student.getPublicUuid(), student));
		return page.map(invoice -> {
			InvoiceResponse response = InvoiceResponse.from(invoice, List.of());
			Student student = studentsByUuid.get(invoice.getStudentId());
			return student == null ? response : response.withStudentIdentity(
					new InvoiceResponse.StudentIdentity(
							student.fullName(), student.getDocumentType(), student.getDocumentNumber()));
		});
	}

	@Transactional(readOnly = true)
	public Page<InvoiceReviewQueueItemResponse> listInvoiceReviewQueue(Pageable pageable) {
		TenantContext.currentRequired();
		Page<Invoice> page = invoiceRepo.findByStatusOrderByEvidenceSubmittedAtDesc(
				Invoice.Status.PENDING_REVIEW, sanitizeReviewQueuePageable(pageable));
		List<UUID> studentUuids = page.getContent().stream()
				.map(Invoice::getStudentId).distinct().toList();
		Map<UUID, Student> studentsByUuid = new HashMap<>();
		studentRepository.findAllByPublicUuidIn(studentUuids)
				.forEach(student -> studentsByUuid.put(student.getPublicUuid(), student));
		return page.map(invoice -> {
			Student student = studentsByUuid.get(invoice.getStudentId());
			InvoiceReviewQueueItemResponse.StudentIdentity identity = student == null ? null
					: new InvoiceReviewQueueItemResponse.StudentIdentity(
							student.fullName(), student.getDocumentType(), student.getDocumentNumber());
			return new InvoiceReviewQueueItemResponse(
					invoice.getPublicUuid(), invoice.getStudentId(), invoice.getPeriodLabel(),
					invoice.getCurrency(), invoice.getTotalCents(), invoice.getStatus(),
					invoice.getDueAt(), invoice.getEvidenceFilePublicUuid(),
					invoice.getEvidenceSubmittedAt(), invoice.getEvidenceNotes(), identity);
		});
	}

	private static Pageable sanitizeReviewQueuePageable(Pageable pageable) {
		int page = Math.max(pageable.getPageNumber(), 0);
		int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
		return PageRequest.of(page, size);
	}

	@Transactional
	public InvoiceResponse emitInvoice(EmitInvoiceRequest request) {
		TenantContext.currentRequired();
		Student student = studentRepository.findByPublicUuid(request.studentPublicUuid())
				.orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "Student not found"));
		PaymentConcept concept = resolveConcept(request.conceptPublicUuid());
		long amount = request.amountCents() > 0
				? request.amountCents()
				: (concept != null ? concept.getDefaultAmountCents() : 0);
		if (amount <= 0) {
			throw new BusinessException("INVALID_AMOUNT", "amountCents must be > 0 (or set a concept default)");
		}
		UUID guardianUserId = resolveGuardianUserId(student);
		String period = request.periodLabel().trim();
		String conceptKey = concept != null ? concept.getCode() : "MANUAL";
		String idemKey = "emit:" + student.getPublicUuid() + ":" + conceptKey + ":" + period;
		if (invoiceRepo.findByIdempotencyKey(idemKey).isPresent()) {
			throw new BusinessException(
					"INVOICE_ALREADY_EXISTS",
					"An invoice already exists for this student/concept/period");
		}
		long discount = Math.min(request.discountCents(), amount);
		Invoice inv = persistInvoice(
				student,
				guardianUserId,
				idemKey,
				period,
				concept != null ? concept.getCurrency() : "PEN",
				amount,
				discount,
				request.dueAt(),
				request.notes(),
				concept != null ? concept.getName() : ("Cuota " + period));
		publishPaymentDue(inv, concept != null ? concept.getName() : ("Cuota " + period));
		List<InvoiceItemResponse> items = itemRepo.findByInvoiceIdOrderByCreatedAtAsc(inv.getId())
				.stream().map(InvoiceItemResponse::from).toList();
		return InvoiceResponse.from(inv, items);
	}

	@Transactional
	public BulkEmitInvoicesResponse bulkEmit(BulkEmitInvoicesRequest request) {
		TenantContext.currentRequired();
		if (request.sectionPublicUuid() == null && !request.allEnrolled()) {
			throw new BusinessException(
					"BULK_SCOPE_REQUIRED",
					"Provide sectionPublicUuid or set allEnrolled=true");
		}
		PaymentConcept concept = resolveConcept(request.conceptPublicUuid());
		long amount = request.amountCents() != null && request.amountCents() > 0
				? request.amountCents()
				: (concept != null ? concept.getDefaultAmountCents() : 0);
		if (amount <= 0) {
			throw new BusinessException("INVALID_AMOUNT", "amountCents must be > 0 (or set a concept default)");
		}
		List<Student> students = resolveBulkStudents(request);
		if (students.size() > BULK_MAX) {
			throw new BusinessException(
					"BULK_LIMIT_EXCEEDED",
					"Bulk emit supports at most " + BULK_MAX + " students (got " + students.size() + ")");
		}
		String period = request.periodLabel().trim();
		String conceptKey = concept != null ? concept.getCode() : "MANUAL";
		String currency = concept != null ? concept.getCurrency() : "PEN";
		String description = concept != null ? concept.getName() : ("Cuota " + period);
		long discount = Math.min(request.discountCents(), amount);

		int skippedDup = 0;
		int skippedNoGuardian = 0;
		List<UUID> created = new ArrayList<>();
		for (Student student : students) {
			String idemKey = "emit:" + student.getPublicUuid() + ":" + conceptKey + ":" + period;
			if (invoiceRepo.findByIdempotencyKey(idemKey).isPresent()) {
				skippedDup++;
				continue;
			}
			UUID guardianUserId;
			try {
				guardianUserId = resolveGuardianUserId(student);
			} catch (BusinessException ex) {
				skippedNoGuardian++;
				continue;
			}
			if (request.dryRun()) {
				created.add(student.getPublicUuid());
				continue;
			}
			Invoice inv = persistInvoice(
					student, guardianUserId, idemKey, period, currency,
					amount, discount, request.dueAt(), request.notes(), description);
			publishPaymentDue(inv, description);
			created.add(inv.getPublicUuid());
		}
		int createdCount = request.dryRun() ? 0 : created.size();
		int candidateCount = students.size();
		return new BulkEmitInvoicesResponse(
				request.dryRun(),
				candidateCount,
				createdCount,
				skippedDup,
				skippedNoGuardian,
				request.dryRun() ? List.of() : List.copyOf(created));
	}

	@Transactional
	public void markPaidCash(UUID invoicePublicUuid, String notes) {
		TenantContext.currentRequired();
		Invoice invoice = invoiceRepo.findByPublicUuidForUpdate(invoicePublicUuid)
				.orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoicePublicUuid));

		if (invoice.getStatus() == Invoice.Status.PAID) {
			throw new com.edushift.shared.exception.BusinessException(
					"INVOICE_ALREADY_PAID", "Invoice is already paid");
		}
		if (invoice.getStatus() == Invoice.Status.CANCELLED) {
			throw new com.edushift.shared.exception.BusinessException(
					"INVOICE_CANCELLED", "Cancelled invoices cannot be marked paid");
		}
		if (invoice.getStatus() == Invoice.Status.REFUNDED) {
			throw new com.edushift.shared.exception.BusinessException(
					"INVOICE_REFUNDED", "Refunded invoices cannot be marked paid");
		}
		if (invoice.getStatus() != Invoice.Status.PENDING
				&& invoice.getStatus() != Invoice.Status.OVERDUE) {
			throw new BusinessException(
					"INVOICE_EVIDENCE_IN_REVIEW",
					"Resolve the submitted payment evidence before recording cash");
		}

		Payment p = new Payment();
		p.setTenantId(TenantContext.currentRequired());
		p.setInvoiceId(invoice.getId());
		p.setGuardianUserId(invoice.getGuardianUserId());
		p.setProvider(Payment.Provider.CASH);
		p.setExternalReference(invoice.getPublicUuid().toString());
		p.setStatus(Payment.Status.APPROVED);
		p.setAmountCents(invoice.getTotalCents());
		p.setCurrency(invoice.getCurrency());
		p.setPaymentMethod("CASH");
		Instant paidAt = Instant.now();
		p.setPaidAt(paidAt);
		p.setRawResponse("{\"manual\":true,\"notes\":\"" + sanitize(notes) + "\"}");
		p = paymentRepo.save(p);

		invoice.setStatus(Invoice.Status.PAID);
		invoice.setPaidAt(paidAt);
		invoice.setEvidenceRejectionReason(null);
		invoiceRepo.save(invoice);
		publishPaymentReceived(invoice);
		log.info("[admin-payment] invoice {} marked PAID via CASH (payment {})",
				invoicePublicUuid, p.getPublicUuid());
	}

	@Transactional
	public void approveEvidence(UUID invoicePublicUuid) {
		TenantContext.currentRequired();
		Invoice invoice = invoiceRepo.findByPublicUuidForUpdate(invoicePublicUuid)
				.orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoicePublicUuid));
		if (invoice.getStatus() != Invoice.Status.PENDING_REVIEW
				|| invoice.getEvidenceFilePublicUuid() == null) {
			throw new BusinessException(
					"INVOICE_NOT_IN_REVIEW",
					"Only an invoice with submitted evidence can be approved");
		}

		Instant paidAt = Instant.now();
		Payment payment = new Payment();
		payment.setTenantId(TenantContext.currentRequired());
		payment.setInvoiceId(invoice.getId());
		payment.setGuardianUserId(invoice.getGuardianUserId());
		payment.setProvider(Payment.Provider.MANUAL);
		payment.setExternalReference(invoice.getPublicUuid().toString());
		payment.setStatus(Payment.Status.APPROVED);
		payment.setAmountCents(invoice.getTotalCents());
		payment.setCurrency(invoice.getCurrency());
		payment.setPaymentMethod("VOUCHER");
		payment.setPaidAt(paidAt);
		payment.setRawResponse("{\"manual\":true,\"source\":\"VOUCHER\",\"evidenceFilePublicUuid\":\""
				+ invoice.getEvidenceFilePublicUuid() + "\"}");
		payment = paymentRepo.save(payment);

		invoice.setStatus(Invoice.Status.PAID);
		invoice.setPaidAt(paidAt);
		invoiceRepo.save(invoice);
		publishPaymentReceived(invoice);
		log.info("[admin-payment] invoice {} approved from voucher (payment {})",
				invoicePublicUuid, payment.getPublicUuid());
	}

	@Transactional
	public void rejectEvidence(UUID invoicePublicUuid, RejectPaymentEvidenceRequest request) {
		TenantContext.currentRequired();
		Invoice invoice = invoiceRepo.findByPublicUuidForUpdate(invoicePublicUuid)
				.orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoicePublicUuid));

		if (invoice.getStatus() != Invoice.Status.PENDING_REVIEW) {
			throw new com.edushift.shared.exception.BusinessException(
					"INVOICE_NOT_IN_REVIEW",
					"Only invoices in PENDING_REVIEW can have evidence rejected (current: "
							+ invoice.getStatus() + ")");
		}

		UUID previousFile = invoice.getEvidenceFilePublicUuid();
		if (previousFile != null) {
			try {
				fileObjectService.releaseReference(previousFile);
			} catch (Exception ex) {
				log.warn("[admin-payment] releaseReference failed for evidence {}: {}",
						previousFile, ex.getMessage());
			}
		}

		Invoice.Status reopen = Instant.now().isAfter(invoice.getDueAt())
				? Invoice.Status.OVERDUE
				: Invoice.Status.PENDING;
		invoice.setStatus(reopen);
		invoice.setEvidenceFilePublicUuid(null);
		invoice.setEvidenceSubmittedAt(null);
		invoice.setEvidenceNotes(null);
		invoice.setEvidenceRejectionReason(request.reason().trim());
		invoiceRepo.save(invoice);
		publishEvidenceRejected(invoice, request.reason().trim());
	}

	@Transactional
	public void refund(UUID invoicePublicUuid, String reason) {
		TenantContext.currentRequired();
		Invoice invoice = invoiceRepo.findByPublicUuid(invoicePublicUuid)
				.orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoicePublicUuid));

		if (invoice.getStatus() != Invoice.Status.PAID) {
			throw new com.edushift.shared.exception.BusinessException(
					"INVOICE_NOT_PAID",
					"Only PAID invoices can be refunded (current: " + invoice.getStatus() + ")");
		}

		Payment payment = paymentRepo.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).stream()
				.filter(p -> p.getStatus() == Payment.Status.APPROVED)
				.findFirst()
				.orElseThrow(() -> new com.edushift.shared.exception.BusinessException(
						"NO_APPROVED_PAYMENT",
						"No APPROVED payment found for invoice " + invoicePublicUuid));

		if (mercadoPagoClient != null && payment.getProvider() == Payment.Provider.MERCADOPAGO
				&& payment.getExternalId() != null) {
			try {
				Map<String, Object> result = mercadoPagoClient.refundPayment(payment.getExternalId());
				log.info("[admin-payment] MP refund OK for payment {}: {}", payment.getPublicUuid(), result);
			}
			catch (Exception ex) {
				log.warn("[admin-payment] MP refund failed for payment {} — proceeding with DB transition: {}",
						payment.getPublicUuid(), ex.getMessage());
			}
		}

		payment.setStatus(Payment.Status.REFUNDED);
		payment.setFailureReason("REFUND: " + sanitize(reason));
		paymentRepo.save(payment);

		invoice.setStatus(Invoice.Status.REFUNDED);
		invoice.setPaidAt(null);
		invoiceRepo.save(invoice);
		log.info("[admin-payment] invoice {} REFUNDED (payment {})",
				invoicePublicUuid, payment.getPublicUuid());
	}

	private void publishPaymentReceived(Invoice inv) {
		var guardian = userRepository.findByPublicUuid(inv.getGuardianUserId()).orElse(null);
		String parentName = guardian == null
				? ""
				: (guardian.getFirstName() + " " + guardian.getLastName()).trim();
		String studentName = studentRepository.findByPublicUuid(inv.getStudentId())
				.map(s -> s.fullName())
				.orElse("");
		String amount = String.format(Locale.US, "%s %.2f",
				inv.getCurrency() == null ? "PEN" : inv.getCurrency(),
				inv.getTotalCents() / 100.0);
		Map<String, Object> payload = new HashMap<>();
		payload.put("invoiceNumber", inv.getPeriodLabel());
		payload.put("parentName", parentName);
		payload.put("concept", "Cuota " + inv.getPeriodLabel());
		payload.put("amount", amount);
		payload.put("studentName", studentName);
		payload.put("invoicePublicUuid", inv.getPublicUuid().toString());
		eventPublisher.publishEvent(
				NotificationEvent.builder()
						.templateKey("PAYMENT_RECEIVED")
						.category(Notification.Category.PAYMENT)
						.sourceId(inv.getPublicUuid())
						.tenantId(inv.getTenantId() != null ? inv.getTenantId() : TenantContext.currentRequired())
						.recipients(List.of(new NotificationEvent.Recipient(
								inv.getGuardianUserId(),
								guardian == null ? null : guardian.getEmail())))
						.payload(payload)
						.build());
	}

	private void publishEvidenceRejected(Invoice inv, String reason) {
		var guardian = userRepository.findByPublicUuid(inv.getGuardianUserId()).orElse(null);
		String parentName = guardian == null
				? ""
				: (guardian.getFirstName() + " " + guardian.getLastName()).trim();
		Map<String, Object> payload = new HashMap<>();
		payload.put("invoiceNumber", inv.getPeriodLabel());
		payload.put("parentName", parentName);
		payload.put("reason", reason);
		payload.put("invoicePublicUuid", inv.getPublicUuid().toString());
		eventPublisher.publishEvent(
				NotificationEvent.builder()
						.templateKey("PAYMENT_EVIDENCE_REJECTED")
						.category(Notification.Category.PAYMENT)
						.sourceId(inv.getPublicUuid())
						.tenantId(inv.getTenantId() != null ? inv.getTenantId() : TenantContext.currentRequired())
						.recipients(List.of(new NotificationEvent.Recipient(
								inv.getGuardianUserId(),
								guardian == null ? null : guardian.getEmail())))
						.payload(payload)
						.build());
	}

	private Invoice persistInvoice(
			Student student,
			UUID guardianUserId,
			String idemKey,
			String period,
			String currency,
			long amountCents,
			long discountCents,
			Instant dueAt,
			String notes,
			String lineDescription) {
		long total = Math.max(0, amountCents - discountCents);
		Invoice inv = new Invoice();
		inv.setTenantId(TenantContext.currentRequired());
		inv.setStudentId(student.getPublicUuid());
		inv.setGuardianUserId(guardianUserId);
		inv.setIdempotencyKey(idemKey);
		inv.setPeriodLabel(period);
		inv.setCurrency(currency == null || currency.isBlank() ? "PEN" : currency);
		inv.setSubtotalCents(amountCents);
		inv.setDiscountCents(discountCents);
		inv.setTaxCents(0);
		inv.setTotalCents(total);
		inv.setStatus(Invoice.Status.PENDING);
		inv.setIssuedAt(Instant.now());
		inv.setDueAt(dueAt != null ? dueAt : Instant.now().plus(15, ChronoUnit.DAYS));
		inv.setNotes(notes);
		inv = invoiceRepo.save(inv);

		InvoiceItem item = new InvoiceItem();
		item.setTenantId(inv.getTenantId());
		item.setInvoiceId(inv.getId());
		item.setDescription(lineDescription);
		item.setQuantity(1);
		item.setUnitAmountCents(amountCents);
		item.setLineTotalCents(amountCents);
		itemRepo.save(item);
		return inv;
	}

	private PaymentConcept resolveConcept(UUID conceptPublicUuid) {
		if (conceptPublicUuid == null) {
			return null;
		}
		return conceptRepo.findByPublicUuidAndDeletedFalse(conceptPublicUuid)
				.orElseThrow(() -> new PaymentConceptNotFoundException(
						"Payment concept not found: " + conceptPublicUuid));
	}

	private UUID resolveGuardianUserId(Student student) {
		List<StudentGuardian> links = studentGuardianRepository.findActiveByStudentId(student.getId());
		StudentGuardian primary = links.stream()
				.filter(StudentGuardian::isPrimaryContact)
				.filter(sg -> sg.getGuardian() != null && sg.getGuardian().getUserId() != null)
				.findFirst()
				.orElseGet(() -> links.stream()
						.filter(sg -> sg.getGuardian() != null && sg.getGuardian().getUserId() != null)
						.findFirst()
						.orElse(null));
		if (primary == null || primary.getGuardian().getUserId() == null) {
			throw new BusinessException(
					"GUARDIAN_NOT_LINKED",
					"Student has no guardian with a portal user account");
		}
		return primary.getGuardian().getUserId();
	}

	private List<Student> resolveBulkStudents(BulkEmitInvoicesRequest request) {
		if (request.sectionPublicUuid() != null) {
			Section section = sectionRepository.findByPublicUuid(request.sectionPublicUuid())
					.orElseThrow(() -> new BusinessException("SECTION_NOT_FOUND", "Section not found"));
			Set<UUID> seen = new LinkedHashSet<>();
			List<Student> out = new ArrayList<>();
			enrollmentRepository.findActiveBySection(section).forEach(e -> {
				Student s = e.getStudent();
				if (s != null && seen.add(s.getId())) {
					out.add(s);
				}
			});
			return out;
		}
		return studentRepository.findByEnrollmentStatus(EnrollmentStatus.ENROLLED);
	}

	private void publishPaymentDue(Invoice inv, String conceptName) {
		var guardian = userRepository.findByPublicUuid(inv.getGuardianUserId()).orElse(null);
		String parentName = guardian == null
				? ""
				: (guardian.getFirstName() + " " + guardian.getLastName()).trim();
		String studentName = studentRepository.findByPublicUuid(inv.getStudentId())
				.map(Student::fullName)
				.orElse("");
		String amount = String.format(Locale.US, "%s %.2f",
				inv.getCurrency() == null ? "PEN" : inv.getCurrency(),
				inv.getTotalCents() / 100.0);
		Map<String, Object> payload = new HashMap<>();
		payload.put("invoiceNumber", inv.getPeriodLabel());
		payload.put("parentName", parentName);
		payload.put("concept", conceptName);
		payload.put("dueDate", inv.getDueAt() == null ? "" : inv.getDueAt().toString());
		payload.put("amount", amount);
		payload.put("invoicePublicUuid", inv.getPublicUuid().toString());
		payload.put("studentPublicUuid", inv.getStudentId().toString());
		payload.put("studentName", studentName);
		eventPublisher.publishEvent(
				NotificationEvent.builder()
						.templateKey("PAYMENT_DUE")
						.category(Notification.Category.PAYMENT)
						.sourceId(inv.getPublicUuid())
						.tenantId(inv.getTenantId() != null ? inv.getTenantId() : TenantContext.currentRequired())
						.recipients(List.of(new NotificationEvent.Recipient(
								inv.getGuardianUserId(),
								guardian == null ? null : guardian.getEmail())))
						.payload(payload)
						.build());
	}

	private static String sanitize(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ").replace("\r", " ");
	}
}
