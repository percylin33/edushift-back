package com.edushift.modules.payments.service;

import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.Payment;
import com.edushift.modules.payments.exception.InvoiceNotFoundException;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.payments.repository.PaymentRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin payment operations (Sprint cierre-A / B5):
 * <ul>
 *   <li>{@link #markPaidCash(UUID, String)} — admin records a cash
 *       payment for an invoice; creates a {@link Payment} row with
 *       {@code provider=CASH, status=APPROVED} and flips the invoice
 *       to {@code PAID}.</li>
 *   <li>{@link #refund(UUID, String)} — admin requests a refund for
 *       an APPROVED payment; flips the payment to REFUNDED and the
 *       invoice to REFUNDED. MercadoPago API integration is wired but
 *       best-effort (logs WARN on failure; the DB transition is the
 *       source of truth).</li>
 * </ul>
 *
 * <p>Both flows are tenant-scoped: invoice / payment lookups go
 * through tenant-aware repositories. Cross-tenant attempts surface
 * as {@link InvoiceNotFoundException} (404, anti-enumeration).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

	private final InvoiceRepository invoiceRepo;
	private final PaymentRepository paymentRepo;

	@Autowired(required = false)
	private MercadoPagoClient mercadoPagoClient;

	@Transactional
	public void markPaidCash(UUID invoicePublicUuid, String notes) {
		TenantContext.currentRequired();
		Invoice invoice = invoiceRepo.findByPublicUuid(invoicePublicUuid)
				.orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoicePublicUuid));

		if (invoice.getStatus() == Invoice.Status.PAID) {
			throw new com.edushift.shared.exception.BusinessException(
					"INVOICE_ALREADY_PAID", "Invoice is already paid");
		}
		if (invoice.getStatus() == Invoice.Status.CANCELLED) {
			throw new com.edushift.shared.exception.BusinessException(
					"INVOICE_CANCELLED", "Cancelled invoices cannot be marked paid");
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
		p.setPaidAt(Instant.now());
		p.setRawResponse("{\"manual\":true,\"notes\":\"" + sanitize(notes) + "\"}");
		p = paymentRepo.save(p);

		invoice.setStatus(Invoice.Status.PAID);
		invoice.setPaidAt(Instant.now());
		invoiceRepo.save(invoice);
		log.info("[admin-payment] invoice {} marked PAID via CASH (payment {})",
				invoicePublicUuid, p.getPublicUuid());
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

		// Locate the most recent APPROVED payment; refund the whole invoice.
		Payment payment = paymentRepo.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).stream()
				.filter(p -> p.getStatus() == Payment.Status.APPROVED)
				.findFirst()
				.orElseThrow(() -> new com.edushift.shared.exception.BusinessException(
						"NO_APPROVED_PAYMENT",
						"No APPROVED payment found for invoice " + invoicePublicUuid));

		// Best-effort MercadoPago refund call. Failure logs but does not block
		// the DB transition — admin override is the source of truth here.
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

	private static String sanitize(String s) {
		if (s == null) return "";
		return s.replace("\"", "'").replace("\n", " ").replace("\r", " ");
	}
}