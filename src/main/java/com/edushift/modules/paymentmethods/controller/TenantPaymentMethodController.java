package com.edushift.modules.paymentmethods.controller;

import com.edushift.modules.paymentmethods.dto.PaymentMethodAdminResponse;
import com.edushift.modules.paymentmethods.dto.PaymentMethodCreateRequest;
import com.edushift.modules.paymentmethods.dto.PaymentMethodPublicResponse;
import com.edushift.modules.paymentmethods.dto.PaymentMethodUpdateRequest;
import com.edushift.modules.paymentmethods.service.TenantPaymentMethodService;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenants/{tenantSlug}/payment-methods")
public class TenantPaymentMethodController {

	private final TenantPaymentMethodService service;

	public TenantPaymentMethodController(TenantPaymentMethodService service) {
		this.service = service;
	}

	// ========== ADMIN ENDPOINTS ==========

	@GetMapping
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	public ResponseEntity<List<PaymentMethodAdminResponse>> listAll(@PathVariable String tenantSlug) {
		UUID tenantId = TenantContext.currentRequired();
		return ResponseEntity.ok(service.listByTenant(tenantId));
	}

	@GetMapping("/{publicUuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	public ResponseEntity<PaymentMethodAdminResponse> getById(
			@PathVariable String tenantSlug,
			@PathVariable UUID publicUuid) {
		UUID tenantId = TenantContext.currentRequired();
		return ResponseEntity.ok(service.getByPublicUuid(tenantId, publicUuid));
	}

	@PostMapping
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	public ResponseEntity<PaymentMethodAdminResponse> create(
			@PathVariable String tenantSlug,
			@Valid @RequestBody PaymentMethodCreateRequest request) {
		UUID tenantId = TenantContext.currentRequired();
		PaymentMethodAdminResponse response = service.create(tenantId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PatchMapping("/{publicUuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	public ResponseEntity<PaymentMethodAdminResponse> update(
			@PathVariable String tenantSlug,
			@PathVariable UUID publicUuid,
			@Valid @RequestBody PaymentMethodUpdateRequest request) {
		UUID tenantId = TenantContext.currentRequired();
		return ResponseEntity.ok(service.update(tenantId, publicUuid, request));
	}

	@DeleteMapping("/{publicUuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	public ResponseEntity<Void> delete(
			@PathVariable String tenantSlug,
			@PathVariable UUID publicUuid) {
		UUID tenantId = TenantContext.currentRequired();
		service.delete(tenantId, publicUuid);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{publicUuid}/set-default")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	public ResponseEntity<PaymentMethodAdminResponse> setDefault(
			@PathVariable String tenantSlug,
			@PathVariable UUID publicUuid) {
		UUID tenantId = TenantContext.currentRequired();
		return ResponseEntity.ok(service.setDefault(tenantId, publicUuid));
	}

	// ========== PUBLIC ENDPOINTS (para apoderados) ==========

	@GetMapping("/public")
	public ResponseEntity<List<PaymentMethodPublicResponse>> listPublic(
			@PathVariable String tenantSlug) {
		UUID tenantId = TenantContext.currentRequired();
		return ResponseEntity.ok(service.listPublicByTenant(tenantId));
	}
}
