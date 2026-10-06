package com.edushift.modules.payments.controller;

import com.edushift.modules.payments.dto.CheckoutResponse;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.payments.dto.PaymentResponse;
import com.edushift.modules.payments.dto.SubmitPaymentEvidenceRequest;
import com.edushift.modules.payments.service.PaymentAccessService;
import com.edushift.modules.payments.service.PaymentService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payments API (Sprint 10 / BE-10.2 + access-status for soft LMS gate).
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentAccessService paymentAccessService;
    private final CurrentUserProvider currentUser;

    @GetMapping("/access-status")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Boolean>> accessStatus() {
        UUID callerId = currentUser.currentUserId()
                .orElseThrow(() -> new com.edushift.shared.exception.UnauthorizedException(
                        "No authenticated user"));
        boolean restricted = paymentAccessService.isLmsRestricted(callerId);
        return ApiResponse.ok(Map.of("lmsRestricted", restricted));
    }

    @GetMapping("/invoices")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<InvoiceResponse>> myInvoices(Pageable pageable) {
        UUID callerId = currentUser.currentUserId()
                .orElseThrow(() -> new com.edushift.shared.exception.UnauthorizedException(
                        "No authenticated user"));
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

    @PostMapping("/invoices/{publicUuid}/evidence")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<InvoiceResponse> submitEvidence(
            @PathVariable UUID publicUuid,
            @Valid @RequestBody SubmitPaymentEvidenceRequest request) {
        return ApiResponse.ok(paymentService.submitEvidence(publicUuid, request));
    }
}
