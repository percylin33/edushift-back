package com.edushift.modules.payments.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Invoice line item (Sprint 10 / BE-10.1).
 *
 * <p>The breakdown of an {@link Invoice}: e.g.
 * "Matrícula 2026" (PEN 200) + "Materiales" (PEN 50) = subtotal PEN 250.
 * The invoice's {@code subtotal_cents} is the sum of its items'
 * {@code line_total_cents}; we cache it on the invoice for quick
 * read API responses.</p>
 */
@Entity
@Table(name = "invoice_items", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceItem extends TenantAwareEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "unit_amount_cents", nullable = false)
    private long unitAmountCents;

    @Column(name = "line_total_cents", nullable = false)
    private long lineTotalCents;

    @PrePersist
    private void onCreate() {
        // line_total = quantity * unit_amount (sign-aware; discounts handled at invoice level)
        if (lineTotalCents == 0) {
            lineTotalCents = (long) quantity * unitAmountCents;
        }
    }
}
