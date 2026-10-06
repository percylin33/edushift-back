package com.edushift.modules.admin.invoicing;

import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform B2B payments (EduShift ↔ school).
 *
 * <p>Lives under {@code /admin/b2b/*} so it does not collide with the
 * tenant LMS ledger at {@code GET /admin/payments} (DEBT-11-PAY-1,
 * {@code LMS_PAYMENT_ADMIN}).</p>
 */
@RestController
@RequestMapping("/admin/b2b")
@Validated
@Tag(name = "Admin B2B Payments", description = "Platform B2B payment management (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class B2BPaymentController {

    private final B2BPaymentService paymentService;

    @GetMapping("/payments")
    @Operation(summary = "List all B2B payments (paginated)")
    public ApiResponse<Page<PaymentResponse>> listPayments(Pageable pageable) {
        return ApiResponse.ok(paymentService.listPayments(pageable));
    }

    @GetMapping("/invoices/{uuid}/payments")
    @Operation(summary = "List B2B payments for an invoice")
    public ApiResponse<List<PaymentResponse>> listByInvoice(@PathVariable("uuid") UUID invoiceId) {
        return ApiResponse.ok(paymentService.listByInvoice(invoiceId));
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a manual B2B payment")
    public ApiResponse<PaymentResponse> register(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.ok(paymentService.registerPayment(request));
    }

    @PostMapping("/payments/{uuid}/refund")
    @Operation(summary = "Refund a B2B payment")
    public ApiResponse<Void> refund(@PathVariable("uuid") UUID paymentId) {
        paymentService.refund(paymentId);
        return ApiResponse.ok();
    }
}
