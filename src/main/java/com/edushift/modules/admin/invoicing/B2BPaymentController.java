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

@RestController
@RequestMapping("/admin")
@Validated
@Tag(name = "Admin Payments", description = "B2B payment management (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class B2BPaymentController {

    private final B2BPaymentService paymentService;

    @GetMapping("/payments")
    @Operation(summary = "List all payments (paginated)")
    public ApiResponse<Page<PaymentResponse>> listPayments(Pageable pageable) {
        return ApiResponse.ok(paymentService.listPayments(pageable));
    }

    @GetMapping("/invoices/{uuid}/payments")
    @Operation(summary = "List payments for an invoice")
    public ApiResponse<List<PaymentResponse>> listByInvoice(@PathVariable("uuid") UUID invoiceId) {
        return ApiResponse.ok(paymentService.listByInvoice(invoiceId));
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a manual payment")
    public ApiResponse<PaymentResponse> register(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.ok(paymentService.registerPayment(request));
    }

    @PostMapping("/payments/{uuid}/refund")
    @Operation(summary = "Refund a payment")
    public ApiResponse<Void> refund(@PathVariable("uuid") UUID paymentId) {
        paymentService.refund(paymentId);
        return ApiResponse.ok();
    }
}
