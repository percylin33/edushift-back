package com.edushift.modules.payments.controller;

import com.edushift.modules.payments.dto.AdminPaymentFilter;
import com.edushift.modules.payments.dto.BulkEmitInvoicesRequest;
import com.edushift.modules.payments.dto.BulkEmitInvoicesResponse;
import com.edushift.modules.payments.dto.EmitInvoiceRequest;
import com.edushift.modules.payments.dto.PaymentConceptRequest;
import com.edushift.modules.payments.dto.PaymentConceptResponse;
import com.edushift.modules.payments.dto.BillingPlanRuleRequest;
import com.edushift.modules.payments.dto.BillingPlanRuleResponse;
import com.edushift.modules.payments.dto.UpdateBillingPlanRuleRequest;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.payments.dto.InvoiceReviewQueueItemResponse;
import com.edushift.modules.payments.dto.PaymentPolicyDto;
import com.edushift.modules.payments.dto.PaymentAgingReportDto;
import com.edushift.modules.payments.dto.PaymentAccessCandidateDto;
import com.edushift.modules.payments.dto.PaymentAccessOverrideDto;
import com.edushift.modules.payments.dto.PaymentResponse;
import com.edushift.modules.payments.dto.RejectPaymentEvidenceRequest;
import com.edushift.modules.payments.dto.SubmitPaymentEvidenceRequest;
import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.Payment;
import com.edushift.modules.payments.entity.PaymentAccessOverride;
import com.edushift.modules.payments.service.AdminPaymentService;
import com.edushift.modules.payments.service.MoraService;
import com.edushift.modules.payments.service.PaymentAccessService;
import com.edushift.modules.payments.service.PaymentConceptService;
import com.edushift.modules.payments.service.PaymentPolicyService;
import com.edushift.modules.payments.service.PaymentReportService;
import com.edushift.modules.payments.service.SiblingDiscountService;
import com.edushift.modules.payments.service.SubscriptionService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.LmsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant admin payments console (Sprint cierre-A / B5).
 *
 * <p>Each operation uses a granular payment authority where available.
 * {@code TENANT_ADMIN} and existing holders of
 * {@link LmsAuthorities#LMS_PAYMENT_ADMIN} retain transitional access.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET    /admin/payments} — tenant payment ledger (paginated, DEBT-11-PAY-1)</li>
 *   <li>{@code GET    /admin/payments/concepts} — list tenant concepts (paginated)</li>
 *   <li>{@code POST   /admin/payments/concepts} — create</li>
 *   <li>{@code PATCH  /admin/payments/concepts/{uuid}} — update</li>
 *   <li>{@code DELETE /admin/payments/concepts/{uuid}} — soft-delete</li>
 *   <li>{@code POST   /admin/payments/invoices/{uuid}/approve-evidence} — approve a submitted voucher</li>
 *   <li>{@code POST   /admin/payments/invoices/{uuid}/mark-paid-cash} — manual cash</li>
 *   <li>{@code POST   /admin/payments/invoices/{uuid}/refund} — refund (MP best-effort)</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/payments")
@Tag(name = "Admin Payments", description = "Tenant admin payments console (Sprint cierre-A / B5)")
@RequiredArgsConstructor
public class AdminPaymentController {

	private final PaymentConceptService conceptService;
	private final AdminPaymentService adminPaymentService;
	private final SubscriptionService subscriptionService;
	private final SiblingDiscountService siblingDiscountService;
	private final PaymentPolicyService paymentPolicyService;
	private final MoraService moraService;
	private final PaymentAccessService paymentAccessService;
	private final PaymentReportService paymentReportService;

	@GetMapping
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "List tenant payments (paginated; optional status/provider/search)")
	public ApiResponse<Page<PaymentResponse>> listPayments(
			@RequestParam(required = false) Payment.Status status,
			@RequestParam(required = false) Payment.Provider provider,
			@RequestParam(required = false) String search,
			Pageable pageable) {
		return ApiResponse.ok(adminPaymentService.adminList(
				AdminPaymentFilter.of(status, provider, search), pageable));
	}

	@GetMapping("/concepts")
	@PreAuthorize(PaymentAccessExpressions.CONCEPTS_READ)
	@Operation(summary = "List tenant payment concepts (paginated)")
	public ApiResponse<Page<PaymentConceptResponse>> listConcepts(Pageable pageable) {
		return ApiResponse.ok(conceptService.list(pageable));
	}

	@PostMapping("/concepts")
	@PreAuthorize(PaymentAccessExpressions.CONCEPTS_MANAGE)
	@Operation(summary = "Create a payment concept")
	public ApiResponse<PaymentConceptResponse> createConcept(@Valid @RequestBody PaymentConceptRequest req) {
		return ApiResponse.ok(conceptService.create(req));
	}

	@PatchMapping("/concepts/{publicUuid}")
	@PreAuthorize(PaymentAccessExpressions.CONCEPTS_MANAGE)
	@Operation(summary = "Update a payment concept")
	public ApiResponse<PaymentConceptResponse> updateConcept(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody PaymentConceptRequest req) {
		return ApiResponse.ok(conceptService.update(publicUuid, req));
	}

	@DeleteMapping("/concepts/{publicUuid}")
	@PreAuthorize(PaymentAccessExpressions.CONCEPTS_MANAGE)
	@Operation(summary = "Soft-delete a payment concept (preserves audit trail)")
	public ApiResponse<Void> deleteConcept(@PathVariable UUID publicUuid) {
		conceptService.softDelete(publicUuid);
		return ApiResponse.ok(null);
	}

	@PostMapping("/invoices")
	@PreAuthorize(PaymentAccessExpressions.ISSUE)
	@Operation(summary = "Emit one invoice for a student")
	public ApiResponse<InvoiceResponse> emitInvoice(@Valid @RequestBody EmitInvoiceRequest request) {
		return ApiResponse.ok(adminPaymentService.emitInvoice(request));
	}

	@PostMapping("/invoices/bulk")
	@PreAuthorize(PaymentAccessExpressions.ISSUE)
	@Operation(summary = "Emit invoices in bulk (section or all enrolled); dryRun previews")
	public ApiResponse<BulkEmitInvoicesResponse> bulkEmit(@Valid @RequestBody BulkEmitInvoicesRequest request) {
		return ApiResponse.ok(adminPaymentService.bulkEmit(request));
	}

	@PostMapping("/invoices/{publicUuid}/mark-paid-cash")
	@PreAuthorize(PaymentAccessExpressions.RECEIVE)
	@Operation(summary = "Mark invoice as paid in cash (front-desk cashier)")
	public ApiResponse<Void> markPaidCash(
			@PathVariable UUID publicUuid,
			@RequestParam(required = false) String notes) {
		adminPaymentService.markPaidCash(publicUuid, notes);
		return ApiResponse.ok(null);
	}

	@PostMapping("/invoices/{publicUuid}/approve-evidence")
	@PreAuthorize(PaymentAccessExpressions.RECEIVE)
	@Operation(summary = "Approve submitted payment evidence and record a manual payment")
	public ApiResponse<Void> approveEvidence(@PathVariable UUID publicUuid) {
		adminPaymentService.approveEvidence(publicUuid);
		return ApiResponse.ok(null);
	}

	@PostMapping("/invoices/{publicUuid}/evidence")
	@PreAuthorize(PaymentAccessExpressions.RECEIVE)
	@Operation(summary = "Adjuntar un comprobante recibido en ventanilla para revisión")
	public ApiResponse<InvoiceResponse> submitEvidenceByStaff(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody SubmitPaymentEvidenceRequest request) {
		return ApiResponse.ok(adminPaymentService.submitEvidenceByStaff(publicUuid, request));
	}

	@GetMapping("/invoices")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "List tenant invoices (optional status filter, e.g. PENDING_REVIEW)")
	public ApiResponse<Page<InvoiceResponse>> listInvoices(
			@RequestParam(required = false) Invoice.Status status,
			Pageable pageable) {
		return ApiResponse.ok(adminPaymentService.listInvoices(status, pageable));
	}

	@GetMapping("/invoices/review-queue")
	@PreAuthorize(PaymentAccessExpressions.RECONCILE)
	@Operation(summary = "List only invoices with submitted vouchers awaiting review")
	public ApiResponse<Page<InvoiceReviewQueueItemResponse>> listInvoiceReviewQueue(Pageable pageable) {
		return ApiResponse.ok(adminPaymentService.listInvoiceReviewQueue(pageable));
	}

	@PostMapping("/invoices/{publicUuid}/reject-evidence")
	@PreAuthorize(PaymentAccessExpressions.RECONCILE)
	@Operation(summary = "Reject parent payment voucher and reopen the invoice")
	public ApiResponse<Void> rejectEvidence(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody RejectPaymentEvidenceRequest request) {
		adminPaymentService.rejectEvidence(publicUuid, request);
		return ApiResponse.ok(null);
	}

	@PostMapping("/invoices/{publicUuid}/refund")
	@PreAuthorize(PaymentAccessExpressions.REFUND)
	@Operation(summary = "Refund a paid invoice (MP API best-effort; DB transition is authoritative)")
	public ApiResponse<Void> refund(
			@PathVariable UUID publicUuid,
			@RequestParam(required = false) String reason) {
		adminPaymentService.refund(publicUuid, reason);
		return ApiResponse.ok(null);
	}

	// ---- Subscriptions (billing plans) ----

	@GetMapping("/subscriptions")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "List billing plans / subscriptions")
	public ApiResponse<Page<com.edushift.modules.payments.dto.SubscriptionResponse>> listSubscriptions(
			@RequestParam(required = false) com.edushift.modules.payments.entity.Subscription.Status status,
			Pageable pageable) {
		return ApiResponse.ok(subscriptionService.list(status, pageable));
	}

	@PostMapping("/subscriptions")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Create a recurring billing plan for a student")
	public ApiResponse<com.edushift.modules.payments.dto.SubscriptionResponse> createSubscription(
			@Valid @RequestBody com.edushift.modules.payments.dto.CreateSubscriptionRequest request) {
		return ApiResponse.ok(subscriptionService.create(request));
	}

	@PostMapping("/subscriptions/bulk")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Bulk-assign billing plans by academic level or grade")
	public ApiResponse<com.edushift.modules.payments.dto.BulkCreateSubscriptionsResponse> bulkCreateSubscriptions(
			@Valid @RequestBody com.edushift.modules.payments.dto.BulkCreateSubscriptionsRequest request) {
		return ApiResponse.ok(subscriptionService.bulkCreate(request));
	}

	@GetMapping("/subscriptions/rules")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "List tenant automatic billing rules")
	public ApiResponse<List<BillingPlanRuleResponse>> listBillingPlanRules() {
		return ApiResponse.ok(subscriptionService.listBillingPlanRules());
	}

	@PostMapping("/subscriptions/rules/preview")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "Preview an automatic billing rule without creating plans")
	public ApiResponse<BillingPlanRuleResponse> previewBillingPlanRule(
			@Valid @RequestBody BillingPlanRuleRequest request) {
		return ApiResponse.ok(subscriptionService.previewBillingPlanRule(request));
	}

	@PostMapping("/subscriptions/rules")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Create and synchronize an automatic billing rule")
	public ApiResponse<BillingPlanRuleResponse> createBillingPlanRule(
			@Valid @RequestBody BillingPlanRuleRequest request) {
		return ApiResponse.ok(subscriptionService.createBillingPlanRule(request));
	}

	@PatchMapping("/subscriptions/rules/{publicUuid}")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Activate or pause an automatic billing rule")
	public ApiResponse<BillingPlanRuleResponse> updateBillingPlanRule(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateBillingPlanRuleRequest request) {
		return ApiResponse.ok(subscriptionService.updateBillingPlanRule(publicUuid, request));
	}

	@PostMapping("/subscriptions/rules/{publicUuid}/sync")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Synchronize current eligible students with an automatic billing rule")
	public ApiResponse<BillingPlanRuleResponse> synchronizeBillingPlanRule(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(subscriptionService.synchronizeBillingPlanRule(publicUuid));
	}

	@DeleteMapping("/subscriptions/rules/{publicUuid}")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Archive an automatic billing rule")
	public ApiResponse<Void> deleteBillingPlanRule(@PathVariable UUID publicUuid) {
		subscriptionService.deleteBillingPlanRule(publicUuid);
		return ApiResponse.ok(null);
	}

	@GetMapping("/subscriptions/sibling-suggestions")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "List sibling-discount suggestions grouped by guardian (staff review)")
	public ApiResponse<java.util.List<com.edushift.modules.payments.dto.SiblingSuggestionGroupResponse>> siblingSuggestions() {
		return ApiResponse.ok(siblingDiscountService.listSuggestions());
	}

	@PostMapping("/subscriptions/sibling-suggestions/refresh")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Re-detect sibling groups and mark NONE plans as SUGGESTED")
	public ApiResponse<java.util.Map<String, Integer>> refreshSiblingSuggestions() {
		int marked = siblingDiscountService.refreshSuggestions();
		return ApiResponse.ok(java.util.Map.of("markedSuggested", marked));
	}

	@PostMapping("/subscriptions/sibling-suggestions/decide")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Accept or reject sibling discount for students (staff decision)")
	public ApiResponse<java.util.Map<String, Integer>> decideSiblingDiscount(
			@Valid @RequestBody com.edushift.modules.payments.dto.SiblingDiscountDecideRequest request) {
		int updated = siblingDiscountService.decide(request.studentPublicUuids(), request.decision());
		return ApiResponse.ok(java.util.Map.of("updated", updated));
	}

	@PostMapping("/subscriptions/{publicUuid}/pause")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Pause a billing plan")
	public ApiResponse<com.edushift.modules.payments.dto.SubscriptionResponse> pauseSubscription(
			@PathVariable UUID publicUuid) {
		return ApiResponse.ok(subscriptionService.pause(publicUuid));
	}

	@PostMapping("/subscriptions/{publicUuid}/resume")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Resume a paused billing plan")
	public ApiResponse<com.edushift.modules.payments.dto.SubscriptionResponse> resumeSubscription(
			@PathVariable UUID publicUuid) {
		return ApiResponse.ok(subscriptionService.resume(publicUuid));
	}

	@PostMapping("/subscriptions/{publicUuid}/cancel")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Cancel a billing plan")
	public ApiResponse<com.edushift.modules.payments.dto.SubscriptionResponse> cancelSubscription(
			@PathVariable UUID publicUuid) {
		return ApiResponse.ok(subscriptionService.cancel(publicUuid));
	}

	@PostMapping("/subscriptions/run-due")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Issue due subscription invoices now (UAT / admin)")
	public ApiResponse<java.util.Map<String, Integer>> runDueSubscriptions() {
		int issued = subscriptionService.runDueNow();
		return ApiResponse.ok(java.util.Map.of("issued", issued));
	}

	// ---- Policy / mora / access / reports ----

	@GetMapping("/policy")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "Get tenant payment collection policy")
	public ApiResponse<PaymentPolicyDto> getPolicy() {
		return ApiResponse.ok(paymentPolicyService.getCurrent());
	}

	@PutMapping("/policy")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Update tenant payment collection policy")
	public ApiResponse<PaymentPolicyDto> updatePolicy(@Valid @RequestBody PaymentPolicyDto policy) {
		return ApiResponse.ok(paymentPolicyService.updateCurrent(policy));
	}

	@PostMapping("/invoices/{publicUuid}/mora-exempt")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Exempt an invoice from automatic late fees")
	public ApiResponse<Void> moraExempt(@PathVariable UUID publicUuid) {
		moraService.exemptInvoice(publicUuid);
		return ApiResponse.ok(null);
	}

	@PostMapping("/access-overrides")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Temporarily lift LMS soft-gate for a user")
	public ApiResponse<PaymentAccessOverride> createAccessOverride(
			@RequestParam UUID userPublicUuid,
			@RequestParam(required = false) UUID studentPublicUuid,
			@RequestParam java.time.Instant untilAt,
			@RequestParam(required = false) String reason) {
		return ApiResponse.ok(paymentAccessService.createOverride(
				userPublicUuid, studentPublicUuid, untilAt, reason));
	}

	@GetMapping("/access-overrides")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "List active tenant LMS access exceptions")
	public ApiResponse<List<PaymentAccessOverrideDto>> listAccessOverrides() {
		return ApiResponse.ok(paymentAccessService.listActiveOverrides());
	}

	@GetMapping("/access-overrides/candidates")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "Search linked students and guardians for an LMS access exception")
	public ApiResponse<List<PaymentAccessCandidateDto>> searchAccessOverrideCandidates(
			@RequestParam String q) {
		return ApiResponse.ok(paymentAccessService.searchOverrideCandidates(q));
	}

	@DeleteMapping("/access-overrides/{publicUuid}")
	@PreAuthorize(PaymentAccessExpressions.MANAGE)
	@Operation(summary = "Revoke a tenant LMS access exception")
	public ApiResponse<Void> revokeAccessOverride(@PathVariable UUID publicUuid) {
		paymentAccessService.revokeOverride(publicUuid);
		return ApiResponse.ok(null);
	}

	@GetMapping(value = "/reports/export.csv", produces = "text/csv")
	@PreAuthorize(PaymentAccessExpressions.EXPORT)
	@Operation(summary = "Export invoices CSV for the current month/tenant")
	public org.springframework.http.ResponseEntity<String> exportCsv() {
		return org.springframework.http.ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=invoices.csv")
				.body(paymentReportService.exportCsv());
	}

	@GetMapping("/reports/aging")
	@PreAuthorize(PaymentAccessExpressions.READ)
	@Operation(summary = "Aging buckets for OVERDUE invoices (centavos)")
	public ApiResponse<PaymentAgingReportDto> aging() {
		return ApiResponse.ok(paymentReportService.agingBuckets());
	}
}
