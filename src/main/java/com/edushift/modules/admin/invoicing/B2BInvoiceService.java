package com.edushift.modules.admin.invoicing;

import com.edushift.modules.admin.invoicing.B2BInvoice.B2BInvoiceStatus;
import com.edushift.modules.admin.subscriptions.B2BSubscription;
import com.edushift.modules.admin.subscriptions.B2BSubscriptionRepository;
import com.edushift.modules.admin.subscriptions.B2BSubscription.B2BSubscriptionStatus;
import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2BInvoiceService {

    private final B2BInvoiceRepository invoiceRepository;
    private final B2BSubscriptionRepository subscriptionRepository;
    private final PlatformPlanRepository planRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final String COUNT_ACTIVE_STUDENTS_SQL = """
            SELECT COUNT(DISTINCT s.id)
            FROM edushift.students s
            WHERE s.tenant_id = ? AND s.enrollment_status = 'ENROLLED' AND s.deleted = false
            """;

    public InvoiceResponse getById(UUID id) {
        return invoiceRepository.findById(id)
                .map(InvoiceResponse::from)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found"));
    }

    public Page<InvoiceResponse> listInvoices(UUID tenantId, B2BInvoiceStatus status, Pageable pageable) {
        Page<B2BInvoice> page;
        if (tenantId != null && status != null) {
            page = invoiceRepository.findByStatusAndTenantId(status, tenantId, pageable);
        } else if (tenantId != null) {
            page = invoiceRepository.findByTenantId(tenantId, pageable);
        } else if (status != null) {
            page = invoiceRepository.findByStatus(status, pageable);
        } else {
            page = invoiceRepository.findAll(pageable);
        }
        return page.map(InvoiceResponse::from);
    }

    public List<InvoiceResponse> listByTenant(UUID tenantId) {
        return invoiceRepository.findByTenantIdOrderByIssuedAtDesc(tenantId).stream()
                .map(InvoiceResponse::from)
                .toList();
    }

    @Transactional
    public void emitMonthly() {
        List<B2BSubscription> activeSubs = subscriptionRepository.findByStatus(B2BSubscriptionStatus.ACTIVE);
        activeSubs.addAll(subscriptionRepository.findByStatus(B2BSubscriptionStatus.TRIAL));

        LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

        int emitted = 0;
        for (B2BSubscription sub : activeSubs) {
            boolean exists = invoiceRepository.findBySubscriptionIdAndPeriodStartAndPeriodEnd(
                    sub.getId(), periodStart, periodEnd).isPresent();
            if (exists) continue;

            PlatformPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
            if (plan == null || !plan.isActive()) continue;

            Integer studentCount = jdbcTemplate.queryForObject(
                    COUNT_ACTIVE_STUDENTS_SQL, Integer.class, sub.getTenantId());
            int count = studentCount != null ? studentCount : 0;
            int pricePerStudent = plan.getPricePerStudentCents();
            int total = count * pricePerStudent;

            B2BInvoice invoice = new B2BInvoice();
            invoice.setTenantId(sub.getTenantId());
            invoice.setSubscriptionId(sub.getId());
            invoice.setPeriodStart(periodStart);
            invoice.setPeriodEnd(periodEnd);
            invoice.setActiveStudentCount(count);
            invoice.setPricePerStudentCents(pricePerStudent);
            invoice.setSubtotalCents(total);
            invoice.setDiscountCents(0);
            invoice.setTotalCents(total);
            invoice.setStatus(total == 0 ? B2BInvoiceStatus.PAID : B2BInvoiceStatus.PENDING);
            invoice.setIssuedAt(Instant.now());
            invoice.setDueAt(periodEnd.plusDays(15));
            if (total == 0) {
                invoice.setPaidAt(Instant.now());
            }
            invoiceRepository.save(invoice);
            emitted++;
        }
        log.info("[b2b-invoice] emitted {} invoices for period {}/{}", emitted, periodStart, periodEnd);
    }

    @Transactional
    public void applyDunning() {
        LocalDate overdueThreshold = LocalDate.now().minusDays(7);
        List<B2BInvoice> overdue = invoiceRepository.findByStatusAndDueAtBefore(
                B2BInvoiceStatus.PENDING, overdueThreshold);
        for (B2BInvoice inv : overdue) {
            inv.setStatus(B2BInvoiceStatus.OVERDUE);
            invoiceRepository.save(inv);
        }
        if (!overdue.isEmpty()) {
            log.info("[b2b-invoice] dunning: marked {} invoices as OVERDUE", overdue.size());
        }
    }

    @Transactional
    public void markAsPaid(UUID invoiceId, String notes) {
        B2BInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found"));

        if (invoice.getStatus() == B2BInvoiceStatus.PAID) {
            throw new ConflictException("INVOICE_ALREADY_PAID", "Invoice is already paid");
        }

        invoice.setStatus(B2BInvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        if (notes != null) invoice.setNotes(notes);
        invoiceRepository.save(invoice);
    }
}
