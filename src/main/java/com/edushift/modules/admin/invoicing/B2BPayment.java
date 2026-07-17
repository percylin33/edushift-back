package com.edushift.modules.admin.invoicing;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "b2b_payments", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class B2BPayment extends AuditableEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "external_ref", length = 100)
    private String externalRef;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "notes", length = 500)
    private String notes;

    public enum PaymentMethod { TRANSFER, CASH, DEBIT, MERCADOPAGO }

    public enum PaymentStatus { PENDING, APPROVED, REJECTED, REFUNDED }
}
