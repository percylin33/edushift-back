package com.edushift.modules.payments.service;

import com.edushift.modules.payments.dto.PaymentPolicyDto;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPolicyService {

	public static final String SETTINGS_KEY = "paymentPolicy";

	private final TenantRepository tenantRepository;
	private final ObjectMapper objectMapper;

	@Transactional(readOnly = true)
	public PaymentPolicyDto getCurrent() {
		Tenant tenant = mustCurrentTenant();
		return readPolicy(tenant);
	}

	@Transactional
	public PaymentPolicyDto updateCurrent(PaymentPolicyDto policy) {
		Tenant tenant = mustCurrentTenant();
		PaymentPolicyDto normalized = normalize(policy);
		Map<String, Object> settings = new HashMap<>(
				tenant.getSettings() == null ? Map.of() : tenant.getSettings());
		@SuppressWarnings("unchecked")
		Map<String, Object> asMap = objectMapper.convertValue(normalized, Map.class);
		settings.put(SETTINGS_KEY, asMap);
		tenant.setSettings(settings);
		tenantRepository.save(tenant);
		log.info("[payment-policy] updated for tenant={}", tenant.getId());
		return normalized;
	}

	@Transactional(readOnly = true)
	public PaymentPolicyDto getForTenant(UUID tenantId) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
		return readPolicy(tenant);
	}

	private PaymentPolicyDto readPolicy(Tenant tenant) {
		Object raw = tenant.getSettings() == null ? null : tenant.getSettings().get(SETTINGS_KEY);
		if (raw == null) {
			return PaymentPolicyDto.defaults();
		}
		try {
			return normalize(objectMapper.convertValue(raw, PaymentPolicyDto.class));
		} catch (Exception ex) {
			log.warn("[payment-policy] invalid settings, using defaults: {}", ex.getMessage());
			return PaymentPolicyDto.defaults();
		}
	}

	private PaymentPolicyDto normalize(PaymentPolicyDto p) {
		if (p == null) {
			return PaymentPolicyDto.defaults();
		}
		List<Integer> days = p.reminderDaysBeforeDue() == null || p.reminderDaysBeforeDue().isEmpty()
				? List.of(7, 3, 1)
				: new ArrayList<>(p.reminderDaysBeforeDue());
		int grace = Math.max(0, p.moraGraceDays());
		double pct = Math.max(0, p.moraPercent());
		int lockDays = Math.max(0, p.lockOverdueDays());
		long siblingCents = Math.max(0L, p.siblingDiscountCents());
		int siblingMin = Math.max(2, p.siblingMinCount() <= 0 ? 2 : p.siblingMinCount());
		return new PaymentPolicyDto(
				days,
				p.moraEnabled(),
				grace,
				pct,
				p.lockEnabled(),
				lockDays,
				p.siblingDiscountEnabled(),
				siblingCents,
				siblingMin);
	}

	private Tenant mustCurrentTenant() {
		UUID tenantId = TenantContext.currentRequired();
		return tenantRepository.findById(tenantId)
				.orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
	}
}
