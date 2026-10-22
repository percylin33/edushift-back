package com.edushift.modules.paymentmethods.service;

import com.edushift.modules.paymentmethods.dto.PaymentMethodAdminResponse;
import com.edushift.modules.paymentmethods.dto.PaymentMethodCreateRequest;
import com.edushift.modules.paymentmethods.dto.PaymentMethodPublicResponse;
import com.edushift.modules.paymentmethods.dto.PaymentMethodUpdateRequest;
import java.util.List;
import java.util.UUID;

public interface TenantPaymentMethodService {

	List<PaymentMethodAdminResponse> listByTenant(UUID tenantId);

	PaymentMethodAdminResponse getByPublicUuid(UUID tenantId, UUID publicUuid);

	PaymentMethodAdminResponse create(UUID tenantId, PaymentMethodCreateRequest request);

	PaymentMethodAdminResponse update(UUID tenantId, UUID publicUuid, PaymentMethodUpdateRequest request);

	void delete(UUID tenantId, UUID publicUuid);

	PaymentMethodAdminResponse setDefault(UUID tenantId, UUID publicUuid);

	List<PaymentMethodPublicResponse> listPublicByTenant(UUID tenantId);
}
