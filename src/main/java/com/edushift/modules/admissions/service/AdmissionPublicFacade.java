package com.edushift.modules.admissions.service;

import com.edushift.modules.admissions.dto.PublicAdmissionInquiryRequest;
import com.edushift.modules.admissions.dto.PublicAdmissionCatalogResponse;
import com.edushift.modules.admissions.dto.PublicInquiryReceipt;
import com.edushift.modules.admissions.dto.PublicOfferResponse;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Resolves public tenant slugs globally, then executes tenant data access in that scope. */
@Service
@RequiredArgsConstructor
public class AdmissionPublicFacade {
	private final TenantRepository tenantRepository;
	private final AdmissionWorkflowService workflowService;

	public PublicAdmissionCatalogResponse catalog(String tenantSlug) {
		Tenant tenant = activeTenant(tenantSlug);
		return TenantContext.runAs(tenant.getId(), () -> workflowService.publicCatalog(tenant.getName()));
	}

	public PublicInquiryReceipt submit(String tenantSlug, PublicAdmissionInquiryRequest request) {
		Tenant tenant = activeTenant(tenantSlug);
		return TenantContext.runAs(tenant.getId(), () -> workflowService.submitPublicInquiry(request));
	}

	public PublicOfferResponse getOffer(String tenantSlug, String token) {
		Tenant tenant = activeTenant(tenantSlug);
		return TenantContext.runAs(tenant.getId(), () -> workflowService.getPublicOffer(tenant.getName(), token));
	}

	public PublicOfferResponse respond(String tenantSlug, String token, boolean accepted) {
		Tenant tenant = activeTenant(tenantSlug);
		return TenantContext.runAs(tenant.getId(), () -> workflowService.respondToOffer(token, accepted));
	}

	private Tenant activeTenant(String slug) {
		Tenant tenant = tenantRepository.findBySlugIgnoreCase(slug)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));
		if (tenant.getStatus() != TenantStatus.ACTIVE) {
			throw new ResourceNotFoundException("Tenant", slug);
		}
		return tenant;
	}
}
