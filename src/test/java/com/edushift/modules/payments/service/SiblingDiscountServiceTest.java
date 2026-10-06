package com.edushift.modules.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.payments.dto.PaymentPolicyDto;
import com.edushift.modules.payments.entity.Subscription;
import com.edushift.modules.payments.repository.SubscriptionRepository;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SiblingDiscountServiceTest {

	@Mock PaymentPolicyService paymentPolicyService;
	@Mock StudentGuardianRepository studentGuardianRepository;
	@Mock StudentEnrollmentRepository enrollmentRepository;
	@Mock SubscriptionRepository subscriptionRepository;

	@InjectMocks SiblingDiscountService service;

	private final UUID tenantId = UUID.fromString("01900000-0000-7000-8000-000000000001");

	@BeforeEach
	void setTenant() {
		TenantContext.set(tenantId);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void defaultsHaveSiblingDiscountDisabled() {
		PaymentPolicyDto d = PaymentPolicyDto.defaults();
		assertThat(d.siblingDiscountEnabled()).isFalse();
		assertThat(d.siblingDiscountCents()).isZero();
		assertThat(d.siblingMinCount()).isEqualTo(2);
	}

	@Test
	void decideRejectsInvalidStatus() {
		assertThatThrownBy(() ->
						service.decide(List.of(UUID.randomUUID()), Subscription.SiblingDiscountStatus.SUGGESTED))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("APPLIED or REJECTED");
	}

	@Test
	void decideMarksActiveSubscriptions() {
		UUID studentId = UUID.randomUUID();
		Subscription sub = new Subscription();
		sub.setStudentId(studentId);
		sub.setSiblingDiscountStatus(Subscription.SiblingDiscountStatus.SUGGESTED);
		when(subscriptionRepository.findByStudentIdInAndStatus(
						eq(List.of(studentId)), eq(Subscription.Status.ACTIVE)))
				.thenReturn(List.of(sub));
		when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		int updated = service.decide(List.of(studentId), Subscription.SiblingDiscountStatus.APPLIED);

		assertThat(updated).isEqualTo(1);
		assertThat(sub.getSiblingDiscountStatus()).isEqualTo(Subscription.SiblingDiscountStatus.APPLIED);
		verify(subscriptionRepository).save(sub);
	}

	@Test
	void resolvedDiscountCentsOnlyWhenAppliedAndPolicyOn() {
		Subscription sub = new Subscription();
		sub.setTenantId(tenantId);
		sub.setAmountCents(52_000L);
		sub.setSiblingDiscountStatus(Subscription.SiblingDiscountStatus.APPLIED);

		when(paymentPolicyService.getForTenant(tenantId))
				.thenReturn(new PaymentPolicyDto(List.of(7), false, 0, 0, false, 0, true, 2000L, 2));

		assertThat(service.resolvedDiscountCents(sub)).isEqualTo(2000L);

		sub.setSiblingDiscountStatus(Subscription.SiblingDiscountStatus.SUGGESTED);
		assertThat(service.resolvedDiscountCents(sub)).isZero();
	}

	@Test
	void resolvedDiscountCentsCapsAtAmount() {
		Subscription sub = new Subscription();
		sub.setTenantId(tenantId);
		sub.setAmountCents(500L);
		sub.setSiblingDiscountStatus(Subscription.SiblingDiscountStatus.APPLIED);
		when(paymentPolicyService.getForTenant(tenantId))
				.thenReturn(new PaymentPolicyDto(List.of(7), false, 0, 0, false, 0, true, 2000L, 2));

		assertThat(service.resolvedDiscountCents(sub)).isEqualTo(500L);
	}
}
