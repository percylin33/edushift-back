package com.edushift.modules.payments.service;

import com.edushift.modules.payments.dto.PaymentConceptRequest;
import com.edushift.modules.payments.dto.PaymentConceptResponse;
import com.edushift.modules.payments.entity.PaymentConcept;
import com.edushift.modules.payments.exception.PaymentConceptNotFoundException;
import com.edushift.modules.payments.repository.PaymentConceptRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped CRUD for {@link PaymentConcept} (Sprint cierre-A / B5).
 *
 * <p>Multi-tenant safety: all reads/writes go through
 * {@link PaymentConceptRepository} which extends JpaRepository and
 * benefits from Hibernate's {@code @TenantId} filter on
 * {@code TenantAwareEntity}. Cross-tenant lookups by
 * {@code publicUuid} return {@link PaymentConceptNotFoundException}
 * (anti-enumeration).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConceptService {

	private final PaymentConceptRepository repository;

	@Transactional(readOnly = true)
	public Page<PaymentConceptResponse> list(Pageable pageable) {
		TenantContext.currentRequired(); // fail fast if no tenant
		return repository.findByDeletedFalseOrderBySortOrderAscNameAsc(pageable)
				.map(PaymentConceptResponse::from);
	}

	@Transactional
	public PaymentConceptResponse create(PaymentConceptRequest request) {
		TenantContext.currentRequired();
		if (repository.findByCodeAndDeletedFalse(request.code()).isPresent()) {
			throw new com.edushift.shared.exception.BusinessException(
					"PAYMENT_CONCEPT_CODE_EXISTS",
					"A concept with code '" + request.code() + "' already exists in this tenant");
		}
		PaymentConcept c = new PaymentConcept();
		applyRequest(c, request);
		try {
			c = repository.saveAndFlush(c);
		}
		catch (DataIntegrityViolationException dup) {
			log.warn("Concurrent insert caught for concept code {}", request.code());
			throw new com.edushift.shared.exception.BusinessException(
					"PAYMENT_CONCEPT_CODE_EXISTS",
					"A concept with code '" + request.code() + "' already exists in this tenant");
		}
		log.info("[payment-concept] created publicUuid={} code={}", c.getPublicUuid(), c.getCode());
		return PaymentConceptResponse.from(c);
	}

	@Transactional
	public PaymentConceptResponse update(UUID publicUuid, PaymentConceptRequest request) {
		PaymentConcept c = mustFind(publicUuid);
		// Code uniqueness check (allow same code on same row).
		repository.findByCodeAndDeletedFalse(request.code())
				.filter(other -> !other.getPublicUuid().equals(publicUuid))
				.ifPresent(other -> {
					throw new com.edushift.shared.exception.BusinessException(
							"PAYMENT_CONCEPT_CODE_EXISTS",
							"A concept with code '" + request.code() + "' already exists in this tenant");
				});
		applyRequest(c, request);
		c = repository.save(c);
		log.info("[payment-concept] updated publicUuid={}", c.getPublicUuid());
		return PaymentConceptResponse.from(c);
	}

	@Transactional
	public void softDelete(UUID publicUuid) {
		PaymentConcept c = mustFind(publicUuid);
		c.markDeleted();
		repository.save(c);
		log.info("[payment-concept] soft-deleted publicUuid={}", c.getPublicUuid());
	}

	@Transactional(readOnly = true)
	public PaymentConceptResponse get(UUID publicUuid) {
		return PaymentConceptResponse.from(mustFind(publicUuid));
	}

	private PaymentConcept mustFind(UUID publicUuid) {
		return repository.findByPublicUuidAndDeletedFalse(publicUuid)
				.orElseThrow(() -> new PaymentConceptNotFoundException(
						"Payment concept not found: " + publicUuid));
	}

	private static void applyRequest(PaymentConcept c, PaymentConceptRequest r) {
		c.setCode(r.code());
		c.setName(r.name());
		c.setDescription(r.description());
		c.setCategory(r.category());
		c.setDefaultAmountCents(r.defaultAmountCents());
		c.setCurrency(r.currency());
		c.setRecurring(Boolean.TRUE.equals(r.recurring()));
		c.setActive(r.active() == null ? Boolean.TRUE : r.active());
		c.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
	}
}