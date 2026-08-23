package com.edushift.modules.payments.controller;

import com.edushift.modules.payments.dto.CheckoutResponse;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.payments.dto.PaymentResponse;
import com.edushift.modules.payments.service.PaymentService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payments API (Sprint 10 / BE-10.2).
 *
 * <p>User-facing endpoints for guardians (PARENT/STUDENT) to view
 * their invoices and start a checkout flow. Admin endpoints
 * (manual reconciliation, refunds) live in a separate controller
 * (TBD; not in MVP scope).</p>
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserProvider currentUser;

    @GetMapping("/invoices")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<InvoiceResponse>> myInvoices(Pageable pageable) {
        UUID callerId = currentUser.currentUserId()
                .orElseThrow(() -> new com.edushift.shared.exception.UnauthorizedException(
                        "No authenticated user"));
        // DEBT-STUDENT-PRIVACY (Fase 0.2): un STUDENT autenticado que no
        // es guardian del pago recibe ahora las invoices asociadas a
        // su studentId (antes veía lista vacía sin feedback).
        return ApiResponse.ok(
                paymentService.listInvoicesForCaller(callerId, pageable));
    }

    @GetMapping("/invoices/{publicUuid}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(paymentService.getInvoice(publicUuid));
    }

    @GetMapping("/invoices/{publicUuid}/payments")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<PaymentResponse>> listPaymentsForInvoice(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(paymentService.listPaymentsForInvoice(publicUuid));
    }

    /**
     * Start a Checkout Pro redirect flow for the given invoice.
     * Returns the {@code init_point} URL the FE should open in a
     * new tab.
     */
    @PostMapping("/invoices/{publicUuid}/checkout")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CheckoutResponse> checkout(@PathVariable UUID publicUuid) {
        String email = currentUser.currentUsername().orElse(null);
        return ApiResponse.ok(paymentService.startCheckout(publicUuid, email));
    }

    @GetMapping("/invoices/{publicUuid}/receipt")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID publicUuid) {
        byte[] pdf = paymentService.getReceiptPdf(publicUuid);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "comprobante-" + publicUuid + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
