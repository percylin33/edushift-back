package com.edushift.modules.admin.invoicing;

import com.edushift.modules.admin.invoicing.B2BInvoice.B2BInvoiceStatus;
import com.edushift.modules.admin.invoicing.B2BPayment.PaymentStatus;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2BPaymentService {

    private final B2BPaymentRepository paymentRepository;
    private final B2BInvoiceRepository invoiceRepository;

    public Page<PaymentResponse> listPayments(Pageable pageable) {
        return paymentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(PaymentResponse::from);
    }

    public List<PaymentResponse> listByInvoice(UUID invoiceId) {
        return paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional
    public PaymentResponse registerPayment(CreatePaymentRequest request) {
        B2BInvoice invoice = invoiceRepository.findById(request.invoiceId())
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found"));

        if (invoice.getStatus() == B2BInvoiceStatus.PAID) {
            throw new ConflictException("INVOICE_ALREADY_PAID", "Invoice is already paid");
        }

        if (request.amountCents() > invoice.getTotalCents()) {
            throw new BadRequestException("PAYMENT_EXCEEDS_INVOICE",
                    "Payment amount exceeds invoice total");
        }

        if (request.externalRef() != null
                && paymentRepository.existsByExternalRef(request.externalRef())) {
            throw new ConflictException("DUPLICATE_EXTERNAL_REF",
                    "External reference already used");
        }

        B2BPayment payment = new B2BPayment();
        payment.setInvoiceId(request.invoiceId());
        payment.setTenantId(invoice.getTenantId());
        payment.setAmountCents(request.amountCents());
        payment.setPaymentMethod(request.method());
        payment.setExternalRef(request.externalRef());
        payment.setNotes(request.notes());

        payment.setStatus(PaymentStatus.APPROVED);
        payment.setPaidAt(Instant.now());
        payment = paymentRepository.save(payment);

        if (request.amountCents() >= invoice.getTotalCents()) {
            invoice.setStatus(B2BInvoiceStatus.PAID);
            invoice.setPaidAt(Instant.now());
            invoiceRepository.save(invoice);
        }

        log.info("[b2b-payment] registered payment={} for invoice={}, amount={}",
                payment.getId(), request.invoiceId(), request.amountCents());

        return PaymentResponse.from(payment);
    }

    @Transactional
    public void refund(UUID paymentId) {
        B2BPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "Payment not found"));

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new ConflictException("PAYMENT_NOT_APPROVED", "Only approved payments can be refunded");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        B2BInvoice invoice = invoiceRepository.findById(payment.getInvoiceId()).orElse(null);
        if (invoice != null && invoice.getStatus() == B2BInvoiceStatus.PAID) {
            invoice.setStatus(B2BInvoiceStatus.PENDING);
            invoice.setPaidAt(null);
            invoiceRepository.save(invoice);
        }
    }
}
