package com.edushift.modules.paymentmethods.service.impl;

import com.edushift.modules.paymentmethods.dto.PaymentMethodAdminResponse;
import com.edushift.modules.paymentmethods.dto.PaymentMethodCreateRequest;
import com.edushift.modules.paymentmethods.dto.PaymentMethodPublicResponse;
import com.edushift.modules.paymentmethods.dto.PaymentMethodUpdateRequest;
import com.edushift.modules.paymentmethods.entity.TenantPaymentMethod;
import com.edushift.modules.paymentmethods.entity.TenantPaymentMethod.PaymentMethodType;
import com.edushift.modules.paymentmethods.repository.TenantPaymentMethodRepository;
import com.edushift.modules.paymentmethods.security.PaymentEncryptionService;
import com.edushift.modules.paymentmethods.service.TenantPaymentMethodService;
import com.edushift.modules.paymentmethods.validator.PaymentMethodValidator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantPaymentMethodServiceImpl implements TenantPaymentMethodService {

	private static final int MAX_METHODS_PER_TENANT = 5;

	private final TenantPaymentMethodRepository repository;
	private final PaymentEncryptionService encryptionService;
	private final PaymentMethodValidator validator;

	public TenantPaymentMethodServiceImpl(
			TenantPaymentMethodRepository repository,
			PaymentEncryptionService encryptionService,
			PaymentMethodValidator validator) {
		this.repository = repository;
		this.encryptionService = encryptionService;
		this.validator = validator;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PaymentMethodAdminResponse> listByTenant(UUID tenantId) {
		return repository.findActiveByTenant(tenantId).stream()
			.map(this::toAdminResponse)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public PaymentMethodAdminResponse getByPublicUuid(UUID tenantId, UUID publicUuid) {
		TenantPaymentMethod method = repository.findActiveByPublicUuid(tenantId, publicUuid)
			.orElseThrow(() -> new PaymentMethodNotFoundException(publicUuid));
		return toAdminResponse(method);
	}

	@Override
	@Transactional
	public PaymentMethodAdminResponse create(UUID tenantId, PaymentMethodCreateRequest request) {
		validator.validate(request);

		long count = repository.countActiveByTenant(tenantId);
		if (count >= MAX_METHODS_PER_TENANT) {
			throw new PaymentMethodLimitException("Máximo " + MAX_METHODS_PER_TENANT + " métodos por tenant");
		}

		TenantPaymentMethod method = new TenantPaymentMethod();
		method.setTenantId(tenantId);
		method.setMethodType(request.methodType());
		method.setDisplayName(request.displayName());
		method.setBankName(request.bankName());
		method.setAccountHolder(request.accountHolder());
		method.setAccountNumber(encryptionService.encrypt(request.accountNumber()));
		method.setCci(request.cci() != null ? encryptionService.encrypt(request.cci()) : null);
		method.setWalletPhone(request.walletPhone() != null ? encryptionService.encrypt(request.walletPhone()) : null);
		method.setWalletQrUrl(request.walletQrUrl());

		// Keep one active default even when earlier methods were soft-deleted.
		if (repository.findDefaultByTenant(tenantId).isEmpty()) {
			method.setDefaultMethod(true);
		}

		TenantPaymentMethod saved = repository.save(method);
		return toAdminResponse(saved);
	}

	@Override
	@Transactional
	public PaymentMethodAdminResponse update(UUID tenantId, UUID publicUuid, PaymentMethodUpdateRequest request) {
		TenantPaymentMethod method = repository.findActiveByPublicUuid(tenantId, publicUuid)
			.orElseThrow(() -> new PaymentMethodNotFoundException(publicUuid));

		if (request.displayName() != null) method.setDisplayName(request.displayName());
		if (request.bankName() != null) method.setBankName(request.bankName());
		if (request.accountHolder() != null) method.setAccountHolder(request.accountHolder());
		if (request.accountNumber() != null) {
			method.setAccountNumber(encryptionService.encrypt(request.accountNumber()));
		}
		if (request.cci() != null) {
			method.setCci(encryptionService.encrypt(request.cci()));
		}
		if (request.walletPhone() != null) {
			method.setWalletPhone(encryptionService.encrypt(request.walletPhone()));
		}
		if (request.walletQrUrl() != null) method.setWalletQrUrl(request.walletQrUrl());
		if (request.active() != null) method.setActive(request.active());

		TenantPaymentMethod saved = repository.save(method);
		return toAdminResponse(saved);
	}

	@Override
	@Transactional
	public void delete(UUID tenantId, UUID publicUuid) {
		TenantPaymentMethod method = repository.findActiveByPublicUuid(tenantId, publicUuid)
			.orElseThrow(() -> new PaymentMethodNotFoundException(publicUuid));
		method.setDeleted(true);
//method.setDeletedAt(java.time.Instant.now());
		repository.save(method);
	}

	@Override
	@Transactional
	public PaymentMethodAdminResponse setDefault(UUID tenantId, UUID publicUuid) {
		// Desmarcar default actual
		repository.findDefaultByTenant(tenantId).ifPresent(current -> {
			current.setDefaultMethod(false);
			repository.save(current);
		});

		// Marcar nuevo default
		TenantPaymentMethod method = repository.findActiveByPublicUuid(tenantId, publicUuid)
			.orElseThrow(() -> new PaymentMethodNotFoundException(publicUuid));
		method.setDefaultMethod(true);
		TenantPaymentMethod saved = repository.save(method);
		return toAdminResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PaymentMethodPublicResponse> listPublicByTenant(UUID tenantId) {
		return repository.findActiveByTenant(tenantId).stream()
			.map(this::toPublicResponse)
			.toList();
	}

	private PaymentMethodAdminResponse toAdminResponse(TenantPaymentMethod m) {
		return new PaymentMethodAdminResponse(
			m.getPublicUuid(),
			m.getMethodType(),
			m.getDisplayName(),
			m.getBankName(),
			m.getAccountHolder(),
			m.getAccountNumber() != null ? encryptionService.decrypt(m.getAccountNumber()) : null,
			m.getCci() != null ? encryptionService.decrypt(m.getCci()) : null,
			m.getWalletPhone() != null ? encryptionService.decrypt(m.getWalletPhone()) : null,
			m.getWalletQrUrl(),
			m.isActive(),
			m.isDefaultMethod()
		);
	}

	private PaymentMethodPublicResponse toPublicResponse(TenantPaymentMethod m) {
		return new PaymentMethodPublicResponse(
			m.getPublicUuid(),
			m.getMethodType(),
			m.getDisplayName(),
			m.getBankName(),
			m.getAccountHolder(),
			m.getAccountNumber() != null ? encryptionService.decrypt(m.getAccountNumber()) : null,
			m.getCci() != null ? encryptionService.decrypt(m.getCci()) : null,
			m.getWalletPhone() != null ? encryptionService.decrypt(m.getWalletPhone()) : null,
			m.getWalletQrUrl(),
			m.isDefaultMethod()
		);
	}

	public static class PaymentMethodNotFoundException extends RuntimeException {
		public PaymentMethodNotFoundException(UUID uuid) {
			super("Método de pago no encontrado: " + uuid);
		}
	}

	public static class PaymentMethodLimitException extends RuntimeException {
		public PaymentMethodLimitException(String message) {
			super(message);
		}
	}
}
