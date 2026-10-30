package com.edushift.modules.admin.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.exception.AiPlanUnavailableException;
import com.edushift.modules.ai.service.AiEntitlementService;
import com.edushift.modules.ai.service.AiEntitlementService.EffectiveAiPlan;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AdminTenantAiSettingsServiceImplTest {

	private static final UUID TENANT_INTERNAL_UUID =
			UUID.fromString("019ec200-0000-7000-0000-000000000001");
	private static final UUID TENANT_PUBLIC_UUID =
			UUID.fromString("519ec200-0000-4000-8000-000000000001");
	private static final UUID ACTOR_UUID =
			UUID.fromString("019ec200-0000-7000-0000-000000000010");

	@Mock private TenantRepository tenantRepository;
	@Mock private AiEntitlementService entitlementService;
	@Mock private JdbcTemplate jdbcTemplate;
	@Mock private AuditLogger auditLogger;
	@Mock private PlatformTransactionManager transactionManager;

	private AdminTenantAiSettingsServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new AdminTenantAiSettingsServiceImpl(
				tenantRepository, entitlementService, jdbcTemplate, auditLogger, transactionManager);
		Tenant tenant = new Tenant();
		tenant.setId(TENANT_INTERNAL_UUID);
		tenant.setPublicUuid(TENANT_PUBLIC_UUID);
		tenant.setName("Colegio de prueba");
		tenant.setSlug("colegio-prueba");
		tenant.setPlan(TenantPlan.ENTERPRISE);
		tenant.setStatus(TenantStatus.ACTIVE);
		given(tenantRepository.findByPublicUuid(TENANT_PUBLIC_UUID)).willReturn(Optional.of(tenant));
		given(entitlementService.current()).willReturn(enterpriseEntitlement());
	}

	@Test
	void getReturnsPlanEligibilityAndInheritedLimitsWhenSettingsDoNotExist() {
		given(jdbcTemplate.queryForList(anyString(), eq(TENANT_PUBLIC_UUID))).willReturn(List.of());

		var response = service.get(TENANT_PUBLIC_UUID);

		assertThat(response.configured()).isFalse();
		assertThat(response.enabled()).isFalse();
		assertThat(response.planAvailable()).isTrue();
		assertThat(response.planCode()).isEqualTo("ENTERPRISE");
		assertThat(response.dailyRequestLimit()).isEqualTo(1000);
		assertThat(response.monthlyTokenLimit()).isEqualTo(5_000_000L);
		assertThat(response.blockedReason()).isEqualTo("AI_DISABLED");
		verify(jdbcTemplate).queryForList(anyString(), eq(TENANT_PUBLIC_UUID));
	}

	@Test
	void getUsesLowerTenantQuotasWithoutExceedingPlan() {
		given(jdbcTemplate.queryForList(anyString(), eq(TENANT_PUBLIC_UUID))).willReturn(List.of(Map.of(
				"ai_enabled", true,
				"daily_request_quota", 250,
				"monthly_token_quota", 1_000_000L)));

		var response = service.get(TENANT_PUBLIC_UUID);

		assertThat(response.enabled()).isTrue();
		assertThat(response.dailyRequestLimit()).isEqualTo(250);
		assertThat(response.monthlyTokenLimit()).isEqualTo(1_000_000L);
		assertThat(response.blockedReason()).isNull();
	}

	@Test
	void updateCreatesSettingsAndAuditsTheTargetTenant() {
		given(jdbcTemplate.queryForList(anyString(), eq(TENANT_PUBLIC_UUID))).willReturn(List.of());
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

		var response = service.update(TENANT_PUBLIC_UUID, true, ACTOR_UUID);

		assertThat(response.configured()).isTrue();
		assertThat(response.enabled()).isTrue();
		assertThat(response.blockedReason()).isNull();
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(sqlCaptor.capture(), argumentsCaptor.capture());
		assertThat(sqlCaptor.getValue()).contains("ON CONFLICT (tenant_id)");
		assertThat(argumentsCaptor.getValue()[2]).isEqualTo(TENANT_PUBLIC_UUID);
		verify(auditLogger).log(eq(AuditAction.UPDATE), eq("tenant_ai_settings"),
				eq(TENANT_PUBLIC_UUID), eq("AI enabled for tenant"), any(Map.class));
	}

	@Test
	void updateDoesNotEnableWhenPlanHasNoAiAccess() {
		given(entitlementService.current()).willThrow(new AiPlanUnavailableException());

		assertThatThrownBy(() -> service.update(TENANT_PUBLIC_UUID, true, ACTOR_UUID))
				.isInstanceOf(AiPlanUnavailableException.class);

		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
		verify(auditLogger, never()).log(any(), anyString(), any(), anyString(), any(Map.class));
	}

	private static EffectiveAiPlan enterpriseEntitlement() {
		return new EffectiveAiPlan("ENTERPRISE", List.of(
				AiEntitlementService.TUTOR,
				AiEntitlementService.TEACHER,
				AiEntitlementService.FAMILY),
				1000, 5_000_000L, 50, 250, 1_000_000_000L, false);
	}
}
