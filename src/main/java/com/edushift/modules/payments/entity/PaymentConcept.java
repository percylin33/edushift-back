package com.edushift.modules.payments.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tenant-defined billing concept (Sprint cierre-A / B5).
 *
 * <p>Conceptos cobrables configurables por el admin del colegio
 * (matrícula, cuota mensual, materiales, refrigerio, examen extra,
 * etc.). Cuando se emite una factura, el admin selecciona uno o más
 * conceptos y cada uno se materializa como {@code invoice_items} con
 * el {@code default_amount_cents} snapshot del momento.</p>
 *
 * <p>Soft-delete: un concepto NO se elimina duro para preservar
 * auditabilidad de facturas históricas que lo referencian. La UNIQUE
 * constraint sobre {@code (tenant_id, code)} ignora filas borradas
 * (partial index) para permitir re-crear el código tras soft-delete.</p>
 */
@Entity
@Table(name = "payment_concepts", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class PaymentConcept extends TenantAwareEntity {

	public enum Category {
		TUITION, ENROLLMENT, MATERIALS, MEALS, TRANSPORT, EXAM, EVENT, OTHER
	}

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 40)
	private Category category = Category.OTHER;

	@Column(name = "default_amount_cents", nullable = false)
	private long defaultAmountCents = 0;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "PEN";

	@Column(name = "is_recurring", nullable = false)
	private boolean recurring = false;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder = 0;

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
	}
}