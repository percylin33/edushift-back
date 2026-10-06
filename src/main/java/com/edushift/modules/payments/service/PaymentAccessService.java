package com.edushift.modules.payments.service;

import com.edushift.modules.payments.dto.PaymentPolicyDto;
import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.PaymentAccessOverride;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.payments.repository.PaymentAccessOverrideRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft LMS gate for delinquency. Never blocks login or Payments routes —
 * callers must only invoke this from LMS mutation endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAccessService {

	private final PaymentPolicyService paymentPolicyService;
	private final InvoiceRepository invoiceRepository;
	private final PaymentAccessOverrideRepository overrideRepository;
	private final StudentRepository studentRepository;
	private final StudentGuardianRepository studentGuardianRepository;

	@Transactional(readOnly = true)
	public boolean isLmsRestricted(UUID userPublicUuid) {
		PaymentPolicyDto policy = paymentPolicyService.getCurrent();
		if (!policy.lockEnabled()) {
			return false;
		}
		Instant now = Instant.now();
		if (!overrideRepository.findActiveForUser(userPublicUuid, now).isEmpty()) {
			return false;
		}
		Set<UUID> studentPublicUuids = resolveLinkedStudentPublicUuids(userPublicUuid);
		if (studentPublicUuids.isEmpty()) {
			return false;
		}
		Instant threshold = now.minus(Math.max(0, policy.lockOverdueDays()), ChronoUnit.DAYS);
		for (UUID studentPublicUuid : studentPublicUuids) {
			List<Invoice> overdue = invoiceRepository.findOverdueForStudent(studentPublicUuid);
			boolean hits = overdue.stream().anyMatch(inv ->
					inv.getDueAt() != null && inv.getDueAt().isBefore(threshold));
			if (hits) {
				return true;
			}
		}
		return false;
	}

	@Transactional(readOnly = true)
	public void assertLmsAllowed(UUID userPublicUuid) {
		if (isLmsRestricted(userPublicUuid)) {
			throw new BusinessException(
					"PAYMENT_LMS_RESTRICTED",
					"Acceso LMS limitado por cuota vencida. Regulariza el pago en Pagos.");
		}
	}

	@Transactional
	public PaymentAccessOverride createOverride(
			UUID userPublicUuid, UUID studentPublicUuid, Instant untilAt, String reason) {
		TenantContext.currentRequired();
		if (untilAt == null || !untilAt.isAfter(Instant.now())) {
			throw new BusinessException("INVALID_UNTIL", "untilAt must be in the future");
		}
		PaymentAccessOverride o = new PaymentAccessOverride();
		o.setTenantId(TenantContext.currentRequired());
		o.setUserPublicUuid(userPublicUuid);
		o.setStudentPublicUuid(studentPublicUuid);
		o.setUntilAt(untilAt);
		o.setReason(reason);
		return overrideRepository.save(o);
	}

	private Set<UUID> resolveLinkedStudentPublicUuids(UUID userPublicUuid) {
		Set<UUID> ids = new HashSet<>();
		studentRepository.findByUserId(userPublicUuid)
				.ifPresent(s -> ids.add(s.getPublicUuid()));
		studentGuardianRepository.findActiveByGuardianUserId(userPublicUuid)
				.forEach(sg -> {
					if (sg.getStudent() != null) {
						ids.add(sg.getStudent().getPublicUuid());
					}
				});
		return ids;
	}
}
