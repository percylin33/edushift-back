package com.edushift.modules.paymentmethods.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.edushift.modules.paymentmethods.service.TenantPaymentMethodService;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantPaymentMethodControllerTenantIsolationTest {

	@Mock
	private TenantPaymentMethodService service;

	@InjectMocks
	private TenantPaymentMethodController controller;

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void everyOperationUsesAuthenticatedTenantInsteadOfPathSlug() {
		UUID authenticatedTenantId = UUID.randomUUID();
		UUID paymentMethodId = UUID.randomUUID();
		TenantContext.set(authenticatedTenantId);

		controller.listAll("other-school");
		controller.getById("other-school", paymentMethodId);
		controller.create("other-school", null);
		controller.update("other-school", paymentMethodId, null);
		controller.delete("other-school", paymentMethodId);
		controller.setDefault("other-school", paymentMethodId);
		controller.listPublic("other-school");

		verify(service).listByTenant(authenticatedTenantId);
		verify(service).getByPublicUuid(authenticatedTenantId, paymentMethodId);
		verify(service).create(authenticatedTenantId, null);
		verify(service).update(authenticatedTenantId, paymentMethodId, null);
		verify(service).delete(authenticatedTenantId, paymentMethodId);
		verify(service).setDefault(authenticatedTenantId, paymentMethodId);
		verify(service).listPublicByTenant(authenticatedTenantId);
		verifyNoMoreInteractions(service);
	}
}
