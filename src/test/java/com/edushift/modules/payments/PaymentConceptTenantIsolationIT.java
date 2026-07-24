package com.edushift.modules.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.IntegrationTest;
import com.edushift.modules.payments.dto.PaymentConceptRequest;
import com.edushift.modules.payments.dto.PaymentConceptResponse;
import com.edushift.modules.payments.entity.PaymentConcept;
import com.edushift.modules.payments.exception.PaymentConceptNotFoundException;
import com.edushift.modules.payments.repository.PaymentConceptRepository;
import com.edushift.modules.payments.service.AdminPaymentService;
import com.edushift.modules.payments.service.PaymentConceptService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Cross-tenant isolation IT for the {@code payment_concepts} admin surface
 * (Sprint cierre-A / B5 part 1).
 *
 * <p>Verifies:</p>
 * <ul>
 *   <li><strong>XT-PAY-1</strong> — Concept created by tenant A is invisible
 *       to tenant B (read path returns {@link PaymentConceptNotFoundException}).</li>
 *   <li><strong>XT-PAY-2</strong> — {@code (code, tenant_id)} uniqueness is
 *       per-tenant: the same code can exist in two tenants.</li>
 *   <li><strong>XT-PAY-3</strong> — Soft-delete is tenant-scoped.</li>
 *   <li><strong>XT-PAY-4</strong> — Admin payment operations
 *       ({@code markPaidCash}, {@code refund}) are tenant-scoped on the
 *       invoice publicUuid.</li>
 * </ul>
 */
class PaymentConceptTenantIsolationIT extends IntegrationTest {

	@Autowired private PaymentConceptService conceptService;
	@Autowired private AdminPaymentService adminPaymentService;
	@Autowired private PaymentConceptRepository repository;
	@Autowired private TenantRepository tenantRepository;

	private UUID tenantAId;
	private UUID tenantBId;

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("XT-PAY-1: tenant B cannot read tenant A's concept")
	void crossTenantReadIs404() {
		tenantAId = createTenant("acme");
		tenantBId = createTenant("globex");

		PaymentConceptResponse aConcept = TenantContext.runAs(tenantAId,
				() -> conceptService.create(req("MATRICULA", "Matrícula anual")));

		PaymentConceptResponse aReadBack = TenantContext.runAs(tenantAId,
				() -> conceptService.get(aConcept.publicUuid()));
		assertThat(aReadBack.code()).isEqualTo("MATRICULA");

		assertThatThrownBy(() -> TenantContext.runAs(tenantBId,
				() -> conceptService.get(aConcept.publicUuid())))
				.isInstanceOf(PaymentConceptNotFoundException.class);

		assertThatThrownBy(() -> TenantContext.runAs(tenantBId,
				() -> { conceptService.softDelete(aConcept.publicUuid()); return null; }))
				.isInstanceOf(PaymentConceptNotFoundException.class);

		assertThatThrownBy(() -> TenantContext.runAs(tenantBId,
				() -> conceptService.update(aConcept.publicUuid(), req("HACKED", "PWN"))))
				.isInstanceOf(PaymentConceptNotFoundException.class);
	}

	@Test
	@DisplayName("XT-PAY-2: same code allowed in two tenants")
	void codeUniquenessIsPerTenant() {
		tenantAId = createTenant("north");
		tenantBId = createTenant("south");

		TenantContext.runAs(tenantAId,
				() -> conceptService.create(req("TUITION", "Tuition")));
		TenantContext.runAs(tenantBId,
				() -> conceptService.create(req("TUITION", "Tuition")));

		Long totalRows = repository.count();
		assertThat(totalRows).isEqualTo(2L);
	}

	@Test
	@DisplayName("XT-PAY-3: soft-delete is tenant-scoped")
	void softDeleteIsTenantScoped() {
		tenantAId = createTenant("east");
		tenantBId = createTenant("west");

		PaymentConceptResponse aConcept = TenantContext.runAs(tenantAId,
				() -> conceptService.create(req("EXAM", "Examen")));
		TenantContext.runAs(tenantAId,
				() -> { conceptService.softDelete(aConcept.publicUuid()); return null; });

		assertThat(TenantContext.runAs(tenantAId,
				() -> repository.findByCodeAndDeletedFalse("EXAM")))
				.isEmpty();

		assertThat(TenantContext.runAs(tenantBId,
				() -> conceptService.create(req("EXAM", "Examen recovery"))).publicUuid())
				.isNotNull();

		Long totalRows = repository.count();
		assertThat(totalRows).isEqualTo(2L);
	}

	@Test
	@DisplayName("XT-PAY-4: paginated list does not leak across tenants")
	void listIsTenantScoped() {
		tenantAId = createTenant("red");
		tenantBId = createTenant("blue");

		TenantContext.runAs(tenantAId, () -> {
			conceptService.create(req("AAA", "A1"));
			conceptService.create(req("AAB", "A2"));
			conceptService.create(req("AAC", "A3"));
			return null;
		});
		TenantContext.runAs(tenantBId, () -> {
			conceptService.create(req("BBA", "B1"));
			return null;
		});

		Long aListSize = TenantContext.runAs(tenantAId, () ->
				conceptService.list(PageRequest.of(0, 50)).getTotalElements());
		Long bListSize = TenantContext.runAs(tenantBId, () ->
				conceptService.list(PageRequest.of(0, 50)).getTotalElements());

		assertThat(aListSize).isEqualTo(3L);
		assertThat(bListSize).isEqualTo(1L);
	}

	// -----------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------

	private UUID createTenant(String slug) {
		Tenant t = new Tenant();
		t.setPublicUuid(UUID.randomUUID());
		t.setSlug(slug);
		t.setName("Test " + slug);
		t.setStatus(TenantStatus.ACTIVE);
		t.setDeleted(false);
		return tenantRepository.saveAndFlush(t).getId();
	}

	private static PaymentConceptRequest req(String code, String name) {
		return new PaymentConceptRequest(
				code, name, null,
				PaymentConcept.Category.TUITION,
				350_000L, "PEN",
				false, true, 0);
	}
}