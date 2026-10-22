package com.edushift.modules.payments.service;

import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.files.entity.FileUploadStatus;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.event.NotificationEvent;
import com.edushift.modules.payments.dto.CheckoutResponse;
import com.edushift.modules.payments.dto.InvoiceItemResponse;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.payments.dto.PaymentResponse;
import com.edushift.modules.payments.dto.SubmitPaymentEvidenceRequest;
import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.Payment;
import com.edushift.modules.payments.exception.InvoiceNotFoundException;
import com.edushift.modules.payments.repository.InvoiceItemRepository;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.payments.repository.PaymentRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment service (Sprint 10 / BE-10.2, ADR-10.1).
 *
 * <p>Core flow:</p>
 * <ol>
 *   <li>Guardian hits {@code POST /api/v1/payments/checkout/{invoiceUuid}}.</li>
 *   <li>We create a {@code PENDING} {@link Payment} row with
 *       {@code externalReference = invoice.publicUuid} and a
 *       generated MP Checkout Pro preference.</li>
 *   <li>We return the {@code init_point} URL; the FE opens it in a
 *       new tab (Checkout Pro redirect flow).</li>
 *   <li>User pays in MP → MP redirects back to {@code success_url}
 *       and fires an IPN webhook to {@code /api/v1/webhooks/mercadopago}.</li>
 *   <li>Webhook handler calls
 *       {@link #handleMercadoPagoWebhook(String, String, String)}:
 *       we fetch the canonical payment from MP, upsert our
 *       {@code Payment} row, and if APPROVED we mark the invoice
 *       {@code PAID} and fire a {@code PAYMENT_RECEIPT} notification.</li>
 * </ol>
 *
 * <h3>Idempotency (DEBT-10-SEC-1)</h3>
 * The unique index {@code (tenant_id, provider, external_id)} on
 * {@code payments} makes a webhook replay safe: the second webhook
 * with the same MP {@code data.id} hits the index and we update the
 * same row. The MP API call to fetch the canonical status is also
 * idempotent (it's a GET).
 *
 * <h3>Multi-tenant</h3>
 * All lookups are tenant-scoped via the {@code @TenantId} filter.
 * The webhook handler also re-validates the tenant by reading
 * {@link TenantContext#currentRequired()} — the {@code TenantFilter}
 * sets it from the path's tenant slug header.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final InvoiceRepository invoiceRepo;
    private final InvoiceItemRepository itemRepo;
    private final PaymentRepository paymentRepo;
    private final MercadoPagoClient mpClient;
    private final ObjectMapper objectMapper;
    private final StudentRepository studentRepository;
    private final StudentGuardianRepository studentGuardianRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceReceiptPdfGenerator receiptPdfGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final FileObjectService fileObjectService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ----------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID publicUuid) {
        Invoice i = mustFindInvoice(publicUuid);
        // DEBT-STUDENT-PRIVACY (Fase 0.2): refuse to expose invoices that
        // don't belong to the caller (neither as guardian nor as student).
        assertOwnership(i);
        List<InvoiceItemResponse> items = itemRepo.findByInvoiceIdOrderByCreatedAtAsc(i.getId())
                .stream().map(InvoiceItemResponse::from).toList();
        return InvoiceResponse.from(i, items);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> listInvoicesForGuardian(UUID guardianUserId, Pageable pageable) {
        return invoiceRepo.findByGuardianUserIdOrderByIssuedAtDesc(guardianUserId, pageable)
                .map(i -> InvoiceResponse.from(i, List.of())); // items lazy-loaded in detail endpoint
    }

    /**
     * DEBT-STUDENT-PRIVACY (Fase 0.2): list invoices visible to the
     * given user — covers both the guardian-ownership case and the
     * student-ownership case so a STUDENT who isn't a portal-guardian
     * can still see invoices attached to their {@code student_id}.
     *
     * <p>For users who are neither (e.g. TEACHER), returns an empty
     * page.</p>
     */
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> listInvoicesForCaller(UUID userId, Pageable pageable) {
        UUID studentPublicUuid = studentRepository.findPublicUuidByUserId(userId).orElse(null);
        if (studentPublicUuid != null) {
            return invoiceRepo
                    .findByGuardianOrStudentOrderByIssuedAtDesc(userId, studentPublicUuid, pageable)
                    .map(i -> InvoiceResponse.from(i, List.of()));
        }
        List<UUID> linkedStudents = studentGuardianRepository
                .findActiveByGuardianUserId(userId)
                .stream()
                .map(link -> link.getStudent().getPublicUuid())
                .toList();
        if (linkedStudents.isEmpty()) {
            return listInvoicesForGuardian(userId, pageable);
        }
        return invoiceRepo
                .findByGuardianOrStudentsOrderByIssuedAtDesc(userId, linkedStudents, pageable)
                .map(i -> InvoiceResponse.from(i, List.of()));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPaymentsForInvoice(UUID invoicePublicUuid) {
        Invoice i = mustFindInvoice(invoicePublicUuid);
        // DEBT-STUDENT-PRIVACY (Fase 0.2): same ownership gate.
        assertOwnership(i);
        return paymentRepo.findByInvoiceIdOrderByCreatedAtDesc(i.getId())
                .stream().map(PaymentResponse::from).toList();
    }

    // ------------------------------------------------------------ checkout

    /**
     * Start a Checkout Pro flow for the given invoice.
     * Creates a PENDING {@link Payment} row and returns the URL
     * the FE should open.
     */
    @Transactional
    public CheckoutResponse startCheckout(UUID invoicePublicUuid, String guardianEmail) {
        Invoice i = mustFindInvoice(invoicePublicUuid);
        // DEBT-STUDENT-PRIVACY (Fase 0.2): the previous implementation
        // accepted any authenticated caller, letting a STUDENT who
        // happened to know another tenant's invoice publicUuid start
        // a checkout. The ownership gate short-circuits that.
        assertOwnership(i);
        if (i.getStatus() == Invoice.Status.PAID) {
            throw new com.edushift.shared.exception.BusinessException(
                    "INVOICE_ALREADY_PAID", "This invoice is already paid");
        }
        if (i.getStatus() == Invoice.Status.CANCELLED) {
            throw new com.edushift.shared.exception.BusinessException(
                    "INVOICE_CANCELLED", "This invoice has been cancelled");
        }

        String initPoint = mpClient.createPreference(
                i.getPublicUuid().toString(),
                "Cuota EduShift — " + i.getPeriodLabel(),
                i.getTotalCents(),
                i.getCurrency(),
                guardianEmail);

        Payment payment = new Payment();
        payment.setTenantId(TenantContext.currentRequired());
        payment.setInvoiceId(i.getId());
        payment.setGuardianUserId(i.getGuardianUserId());
        payment.setProvider(Payment.Provider.MERCADOPAGO);
        payment.setExternalReference(i.getPublicUuid().toString());
        payment.setStatus(Payment.Status.PENDING);
        payment.setAmountCents(i.getTotalCents());
        payment.setCurrency(i.getCurrency());
        payment = paymentRepo.save(payment);

        return new CheckoutResponse(payment.getPublicUuid(), initPoint, initPoint);
    }

    @Transactional(readOnly = true)
    public byte[] getReceiptPdf(UUID invoicePublicUuid) {
        Invoice invoice = mustFindInvoice(invoicePublicUuid);
        assertOwnership(invoice);
        if (invoice.getStatus() != Invoice.Status.PAID) {
            throw new ResourceNotFoundException("InvoiceReceipt", invoicePublicUuid);
        }

        var tenant = tenantRepository.findById(TenantContext.currentRequired()).orElse(null);
        String schoolName = tenant != null && tenant.getName() != null && !tenant.getName().isBlank()
                ? tenant.getName()
                : "EduShift";
        String logoUrl = brandingString(tenant != null ? tenant.getBranding() : null, "logoUrl");
        String primaryColor = brandingString(tenant != null ? tenant.getBranding() : null, "primaryColor");
        byte[] logoBytes = receiptPdfGenerator.loadLogoBytes(logoUrl);

        String studentName = studentRepository.findByPublicUuid(invoice.getStudentId())
                .map(student -> student.fullName())
                .orElse("—");

        var items = itemRepo.findByInvoiceIdOrderByCreatedAtAsc(invoice.getId());

        return receiptPdfGenerator.generate(new InvoiceReceiptPdfGenerator.ReceiptContext(
                schoolName,
                primaryColor,
                logoBytes,
                studentName,
                invoice,
                items));
    }

    private static String brandingString(java.util.Map<String, Object> branding, String key) {
        if (branding == null || branding.isEmpty()) {
            return null;
        }
        Object value = branding.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }

    /**
     * Parent/student attaches a voucher image/PDF already uploaded via
     * {@code POST /files?module=payments}. Never marks the invoice PAID.
     */
    @Transactional
    public InvoiceResponse submitEvidence(UUID invoicePublicUuid, SubmitPaymentEvidenceRequest request) {
        Invoice invoice = invoiceRepo.findByPublicUuidForUpdate(invoicePublicUuid)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "Invoice not found in the current tenant: " + invoicePublicUuid));
        assertOwnership(invoice);
        if (invoice.getStatus() != Invoice.Status.PENDING
                && invoice.getStatus() != Invoice.Status.OVERDUE
                && invoice.getStatus() != Invoice.Status.PENDING_REVIEW) {
            throw new com.edushift.shared.exception.BusinessException(
                    "INVOICE_NOT_OPEN",
                    "Evidence can only be submitted for pending or overdue invoices (current: "
                            + invoice.getStatus() + ")");
        }

        var file = fileObjectService.getByPublicUuid(request.filePublicUuid());
        if (file.getStatus() != FileUploadStatus.READY) {
            throw new BadRequestException(
                    "EVIDENCE_FILE_NOT_READY",
                    "Payment evidence file is not ready");
        }
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean allowed = mime.startsWith("image/") || "application/pdf".equals(mime);
        if (!allowed) {
            throw new BadRequestException(
                    "EVIDENCE_FILE_TYPE",
                    "Payment evidence must be an image or a PDF");
        }

        UUID previous = invoice.getEvidenceFilePublicUuid();
        if (previous != null && !previous.equals(request.filePublicUuid())) {
            try {
                fileObjectService.releaseReference(previous);
            } catch (Exception ex) {
                log.warn("[payments] releaseReference failed for previous evidence {}: {}",
                        previous, ex.getMessage());
            }
        }
        if (previous == null || !previous.equals(request.filePublicUuid())) {
            fileObjectService.acquireReference(request.filePublicUuid());
        }

        invoice.setEvidenceFilePublicUuid(request.filePublicUuid());
        invoice.setEvidenceSubmittedAt(Instant.now());
        invoice.setEvidenceNotes(blankToNull(request.notes()));
        invoice.setEvidenceRejectionReason(null);
        invoice.setStatus(Invoice.Status.PENDING_REVIEW);
        invoiceRepo.save(invoice);
        publishEvidenceSubmitted(invoice);

        List<InvoiceItemResponse> items = itemRepo.findByInvoiceIdOrderByCreatedAtAsc(invoice.getId())
                .stream().map(InvoiceItemResponse::from).toList();
        return InvoiceResponse.from(invoice, items);
    }

    private void publishEvidenceSubmitted(Invoice inv) {
        UUID tenantId = inv.getTenantId() != null ? inv.getTenantId() : TenantContext.currentRequired();
        List<NotificationEvent.Recipient> recipients = userRepository
                .findActivePaymentAdmins(tenantId)
                .stream()
                .map(u -> new NotificationEvent.Recipient(u.getPublicUuid(), u.getEmail()))
                .toList();
        if (recipients.isEmpty()) {
            log.warn("[payments] no payment admins to notify for evidence on invoice {}",
                    inv.getPublicUuid());
            return;
        }
        String studentName = studentRepository.findByPublicUuid(inv.getStudentId())
                .map(s -> s.fullName())
                .orElse("");
        String amount = String.format(Locale.US, "%s %.2f",
                inv.getCurrency() == null ? "PEN" : inv.getCurrency(),
                inv.getTotalCents() / 100.0);
        Map<String, Object> payload = new HashMap<>();
        payload.put("invoiceNumber", inv.getPeriodLabel());
        payload.put("concept", "Cuota " + inv.getPeriodLabel());
        payload.put("amount", amount);
        payload.put("studentName", studentName);
        payload.put("invoicePublicUuid", inv.getPublicUuid().toString());
        payload.put("notes", inv.getEvidenceNotes() == null ? "" : inv.getEvidenceNotes());
        eventPublisher.publishEvent(
                NotificationEvent.builder()
                        .templateKey("PAYMENT_EVIDENCE_SUBMITTED")
                        .category(Notification.Category.PAYMENT)
                        .sourceId(inv.getPublicUuid())
                        .tenantId(tenantId)
                        .recipients(recipients)
                        .payload(payload)
                        .build());
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // --------------------------------------------------------- webhook

    /**
     * Process a MercadoPago IPN webhook. Idempotent.
     *
     * @param dataId      the {@code data.id} from the webhook payload
     *                    (MercadoPago payment id)
     * @param type        the {@code type} from the payload (e.g. "payment")
     * @param rawPayload  the full raw JSON, stored on the Payment row
     *                    for audit
     */
    @Transactional
    public void handleMercadoPagoWebhook(String dataId, String type, String rawPayload) {
        if (!"payment".equals(type)) {
            log.info("[MP webhook] ignoring non-payment notification: type={}", type);
            return;
        }
        // 1) Fetch canonical status from MP (never trust webhook payload alone).
        JsonNode mpPayment = mpClient.getPayment(dataId);
        String mpStatus = mpPayment.path("status").asText("");
        String mpExternalRef = mpPayment.path("external_reference").asText(null);
        if (mpExternalRef == null || mpExternalRef.isBlank()) {
            log.warn("[MP webhook] payment {} has no external_reference; ignoring", dataId);
            return;
        }

        // 2) Find the invoice + existing payment row (idempotent).
        UUID invoicePublicUuid = UUID.fromString(mpExternalRef);
        Invoice invoice;
        try {
            invoice = mustFindInvoice(invoicePublicUuid);
        } catch (InvoiceNotFoundException e) {
            log.warn("[MP webhook] invoice {} not found in current tenant; ignoring", invoicePublicUuid);
            return;
        }
        Payment payment = paymentRepo
                .findByProviderAndExternalId(Payment.Provider.MERCADOPAGO, dataId)
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setTenantId(TenantContext.currentRequired());
                    p.setInvoiceId(invoice.getId());
                    p.setGuardianUserId(invoice.getGuardianUserId());
                    p.setProvider(Payment.Provider.MERCADOPAGO);
                    p.setExternalId(dataId);
                    p.setExternalReference(mpExternalRef);
                    p.setAmountCents(invoice.getTotalCents());
                    p.setCurrency(invoice.getCurrency());
                    return p;
                });
        payment.setStatus(mapMpStatus(mpStatus));
        payment.setPaymentMethod(mpPayment.path("payment_method_id").asText(null));
        if (mpPayment.hasNonNull("installments")) {
            payment.setInstallments(mpPayment.get("installments").asInt());
        }
        if ("approved".equalsIgnoreCase(mpStatus)) {
            payment.setPaidAt(Instant.now());
        }
        if ("rejected".equalsIgnoreCase(mpStatus)) {
            JsonNode statusDetail = mpPayment.path("status_detail");
            payment.setFailureReason(statusDetail.isMissingNode() ? mpStatus : statusDetail.asText());
        }
        payment.setRawResponse(rawPayload == null ? "{}" : rawPayload);
        payment = paymentRepo.save(payment);

        // 3) If approved, mark the invoice PAID.
        if (payment.getStatus() == Payment.Status.APPROVED && invoice.getStatus() != Invoice.Status.PAID) {
            invoice.setStatus(Invoice.Status.PAID);
            invoice.setPaidAt(Instant.now());
            invoiceRepo.save(invoice);
            log.info("[MP webhook] invoice {} marked PAID (payment {})", invoicePublicUuid, dataId);
        }
    }

    private static Payment.Status mapMpStatus(String mpStatus) {
        if (mpStatus == null) return Payment.Status.PENDING;
        return switch (mpStatus.toLowerCase()) {
            case "approved"             -> Payment.Status.APPROVED;
            case "rejected"             -> Payment.Status.REJECTED;
            case "cancelled", "voided"  -> Payment.Status.CANCELLED;
            case "refunded"             -> Payment.Status.REFUNDED;
            case "in_process", "pending"-> Payment.Status.IN_PROCESS;
            default                     -> Payment.Status.PENDING;
        };
    }

    // ------------------------------------------------------------ helpers

    private Invoice mustFindInvoice(UUID publicUuid) {
        return invoiceRepo.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "Invoice not found in the current tenant: " + publicUuid));
    }

    /**
     * DEBT-STUDENT-PRIVACY (Fase 0.2): refuses read/start-checkout
     * access to invoices that don't belong to the caller (neither as
     * guardian nor as student). Cross-tenant is already filtered by
     * {@code @TenantId}; this is defence-in-depth at the row level.
     *
     * <p>Throws {@link ForbiddenException} (403) — same shape as
     * other ownership checks in the codebase (family-link, etc.).
     * Returning 404 would be a more aggressive anti-enumeration stance
     * but the FE already distinguishes the two codes on the
     * "Mis pagos" page.</p>
     */
    private void assertOwnership(Invoice invoice) {
        UUID caller = currentUserProvider.currentUserId().orElse(null);
        if (caller == null) {
            throw new ForbiddenException("AUTH_REQUIRED",
                    "Authenticated user is required to access this invoice");
        }
        boolean ownsAsGuardian = caller.equals(invoice.getGuardianUserId());
        boolean ownsAsStudent = false;
        if (!ownsAsGuardian) {
            UUID studentPublicUuid = studentRepository.findPublicUuidByUserId(caller).orElse(null);
            ownsAsStudent = studentPublicUuid != null
                    && studentPublicUuid.equals(invoice.getStudentId());
        }
        boolean ownsAsLinkedParent = false;
        if (!ownsAsGuardian && !ownsAsStudent && invoice.getStudentId() != null) {
            ownsAsLinkedParent = studentRepository.findByPublicUuid(invoice.getStudentId())
                    .map(student -> studentGuardianRepository
                            .existsActiveLinkForParent(student.getId(), caller))
                    .orElse(false);
        }
        if (!ownsAsGuardian && !ownsAsStudent && !ownsAsLinkedParent) {
            log.warn("[payments] ownership denied -- caller={} invoice={} student={} guardian={}",
                    caller, invoice.getPublicUuid(),
                    invoice.getStudentId(), invoice.getGuardianUserId());
            throw new ForbiddenException("INVOICE_NOT_OWNED",
                    "Caller is neither the guardian nor the student of this invoice");
        }
    }
}
