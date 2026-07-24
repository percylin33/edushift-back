package com.edushift.modules.payments.controller;

import com.edushift.modules.payments.dto.PaymentConceptRequest;
import com.edushift.modules.payments.dto.PaymentConceptResponse;
import com.edushift.modules.payments.service.AdminPaymentService;
import com.edushift.modules.payments.service.PaymentConceptService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.LmsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant admin payments console (Sprint cierre-A / B5).
 *
 * <p>Gated by {@link LmsAuthorities#LMS_PAYMENT_ADMIN} — granted to
 * {@code TENANT_ADMIN} + {@code STAFF} (front-desk cashiers).
 * Maps to the FE
 * {@code Permission.PaymentAdmin}.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET    /admin/payments/concepts} — list tenant concepts (paginated)</li>
 *   <li>{@code POST   /admin/payments/concepts} — create</li>
 *   <li>{@code PATCH  /admin/payments/concepts/{uuid}} — update</li>
 *   <li>{@code DELETE /admin/payments/concepts/{uuid}} — soft-delete</li>
 *   <li>{@code POST   /admin/payments/invoices/{uuid}/mark-paid-cash} — manual cash</li>
 *   <li>{@code POST   /admin/payments/invoices/{uuid}/refund} — refund (MP best-effort)</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/payments")
@PreAuthorize("hasAuthority('" + LmsAuthorities.LMS_PAYMENT_ADMIN + "')")
@Tag(name = "Admin Payments", description = "Tenant admin payments console (Sprint cierre-A / B5)")
@RequiredArgsConstructor
public class AdminPaymentController {

	private final PaymentConceptService conceptService;
	private final AdminPaymentService adminPaymentService;

	@GetMapping("/concepts")
	@Operation(summary = "List tenant payment concepts (paginated)")
	public ApiResponse<Page<PaymentConceptResponse>> listConcepts(Pageable pageable) {
		return ApiResponse.ok(conceptService.list(pageable));
	}

	@PostMapping("/concepts")
	@Operation(summary = "Create a payment concept")
	public ApiResponse<PaymentConceptResponse> createConcept(@Valid @RequestBody PaymentConceptRequest req) {
		return ApiResponse.ok(conceptService.create(req));
	}

	@PatchMapping("/concepts/{publicUuid}")
	@Operation(summary = "Update a payment concept")
	public ApiResponse<PaymentConceptResponse> updateConcept(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody PaymentConceptRequest req) {
		return ApiResponse.ok(conceptService.update(publicUuid, req));
	}

	@DeleteMapping("/concepts/{publicUuid}")
	@Operation(summary = "Soft-delete a payment concept (preserves audit trail)")
	public ApiResponse<Void> deleteConcept(@PathVariable UUID publicUuid) {
		conceptService.softDelete(publicUuid);
		return ApiResponse.ok(null);
	}

	@PostMapping("/invoices/{publicUuid}/mark-paid-cash")
	@Operation(summary = "Mark invoice as paid in cash (front-desk cashier)")
	public ApiResponse<Void> markPaidCash(
			@PathVariable UUID publicUuid,
			@RequestParam(required = false) String notes) {
		adminPaymentService.markPaidCash(publicUuid, notes);
		return ApiResponse.ok(null);
	}

	@PostMapping("/invoices/{publicUuid}/refund")
	@Operation(summary = "Refund a paid invoice (MP API best-effort; DB transition is authoritative)")
	public ApiResponse<Void> refund(
			@PathVariable UUID publicUuid,
			@RequestParam(required = false) String reason) {
		adminPaymentService.refund(publicUuid, reason);
		return ApiResponse.ok(null);
	}
}