package com.edushift.modules.admin.invoicing;

import com.edushift.modules.admin.invoicing.B2BInvoice.B2BInvoiceStatus;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Validated
@Tag(name = "Admin Invoices", description = "B2B invoice management (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class B2BInvoiceController {

    private final B2BInvoiceService invoiceService;

    @GetMapping("/invoices")
    @Operation(summary = "List invoices (paginated)")
    public ApiResponse<Page<InvoiceResponse>> listInvoices(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) B2BInvoiceStatus status,
            Pageable pageable) {
        return ApiResponse.ok(invoiceService.listInvoices(tenantId, status, pageable));
    }

    @GetMapping("/tenants/{uuid}/invoices")
    @Operation(summary = "List invoices for a tenant")
    public ApiResponse<List<InvoiceResponse>> listByTenant(@PathVariable("uuid") UUID tenantId) {
        return ApiResponse.ok(invoiceService.listByTenant(tenantId));
    }

    @GetMapping("/invoices/{uuid}")
    @Operation(summary = "Get invoice detail")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable("uuid") UUID id) {
        return ApiResponse.ok(invoiceService.getById(id));
    }

    @PostMapping("/invoices/{uuid}/mark-paid")
    @Operation(summary = "Manually mark an invoice as paid")
    public ApiResponse<Void> markPaid(
            @PathVariable("uuid") UUID id,
            @RequestParam(required = false) String notes) {
        invoiceService.markAsPaid(id, notes);
        return ApiResponse.ok();
    }
}
