package com.edushift.modules.admin.invoicing;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "b2b_invoices", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class B2BInvoice extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "active_student_count", nullable = false)
    private int activeStudentCount;

    @Column(name = "price_per_student_cents", nullable = false)
    private int pricePerStudentCents;

    @Column(name = "subtotal_cents", nullable = false)
    private int subtotalCents;

    @Column(name = "discount_cents", nullable = false)
    private int discountCents;

    @Column(name = "total_cents", nullable = false)
    private int totalCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private B2BInvoiceStatus status = B2BInvoiceStatus.PENDING;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "due_at", nullable = false)
    private LocalDate dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "notes", length = 500)
    private String notes;

    public enum B2BInvoiceStatus {
        PENDING, PAID, OVERDUE, CANCELLED, REFUNDED
    }
}
