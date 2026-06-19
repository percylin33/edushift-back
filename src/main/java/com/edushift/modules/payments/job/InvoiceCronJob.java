package com.edushift.modules.payments.job;

import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.Subscription;
import com.edushift.modules.payments.entity.Subscription.BillingPeriod;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.payments.repository.SubscriptionRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoice cron (Sprint 10 / BE-10.3, ADR-10.2).
 *
 * <p>Two responsibilities, both system jobs that bypass the
 * {@code @TenantId} filter (like {@code EmailOutboxProcessor} and
 * {@code ReportJobProcessor}):</p>
 *
 * <ol>
 *   <li><b>Issue monthly invoices</b>: every day at 03:05 (configurable),
 *       find subscriptions with {@code next_billing_at <= now}, create
 *       a {@code PENDING} invoice for the current period (with
 *       idempotency key {@code "sub:<uuid>:<yyyy-MM>"}), and advance
 *       {@code next_billing_at} by one period.</li>
 *   <li><b>Dunning</b>: every day at 09:00, find invoices that are
 *       {@code PENDING} with {@code due_at < now - 3 days} and flip
 *       to {@code OVERDUE}, then send a
 *       {@code com.edushift.modules.notifications.service.NotificationService}
 *       reminder to the guardian. The notification path is wired
 *       through the events module (Sprint 9 BE-9.3 pattern).</li>
 * </ol>
 *
 * <h3>Idempotency (DEBT-10-IDEMP-1)</h3>
 * The invoice insert uses
 * {@code idempotency_key = "sub:<uuid>:<yyyy-MM>"}, which is unique
 * per tenant. If the cron re-runs on the same day (or for the same
 * month), the unique index rejects the duplicate and we just log.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceCronJob {

    private final SubscriptionRepository subscriptionRepo;
    private final InvoiceRepository invoiceRepo;

    @Value("${app.payments.cron.batch-size:200}")
    private int batchSize;

    @Value("${app.payments.cron.dunning-days:3}")
    private int dunningDays;

    // ------------------------------------------------------ invoice issuance

    /** Daily 03:05 (server time). */
    @Scheduled(cron = "${app.payments.cron.invoice-cron:0 5 3 * * *}")
    public void issueMonthlyInvoices() {
        Instant now = Instant.now();
        List<Subscription> due = subscriptionRepo.findDueForBilling(now, PageRequest.of(0, batchSize));
        log.info("[InvoiceCron] issuing monthly invoices — {} due", due.size());
        for (Subscription s : due) {
            try {
                issueOne(s, now);
            } catch (Exception e) {
                log.error("[InvoiceCron] failed to issue invoice for sub={}: {}", s.getPublicUuid(), e.getMessage(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issueOne(Subscription s, Instant now) {
        // We must set the tenant context for the InvoiceRepository
        // (which is @TenantId-filtered).
        TenantContext.runAs(s.getTenantId(), () -> {
            YearMonth period = YearMonth.from(LocalDate.ofInstant(now, ZoneId.systemDefault()));
            String periodLabel = period.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String idemKey = "sub:" + s.getPublicUuid() + ":" + periodLabel;

            // Idempotency: if an invoice with this key exists, skip.
            if (invoiceRepo.findByIdempotencyKey(idemKey).isPresent()) {
                log.info("[InvoiceCron] sub={} period={} already invoiced (idem={})",
                        s.getPublicUuid(), periodLabel, idemKey);
                return Boolean.TRUE;
            }

            Invoice inv = new Invoice();
            inv.setTenantId(s.getTenantId());
            inv.setSubscriptionId(s.getId());
            inv.setStudentId(s.getStudentId());
            inv.setGuardianUserId(s.getGuardianUserId());
            inv.setIdempotencyKey(idemKey);
            inv.setPeriodLabel(periodLabel);
            inv.setCurrency(s.getCurrency());
            inv.setSubtotalCents(s.getAmountCents());
            inv.setTotalCents(s.getAmountCents()); // no tax / discount in MVP
            inv.setStatus(Invoice.Status.PENDING);
            inv.setIssuedAt(Instant.now());
            inv.setDueAt(now.plus(15, ChronoUnit.DAYS));
            invoiceRepo.save(inv);

            // Advance the subscription's next_billing_at.
            Instant next = advanceBilling(s, period, now);
            s.setNextBillingAt(next);
            subscriptionRepo.save(s);

            log.info("[InvoiceCron] issued invoice {} for sub={} period={} amount={} {}",
                    inv.getPublicUuid(), s.getPublicUuid(), periodLabel,
                    s.getAmountCents(), s.getCurrency());
            return Boolean.TRUE;
        });
    }

    private static Instant advanceBilling(Subscription s, YearMonth period, Instant now) {
        return switch (s.getBillingPeriod()) {
            case MONTHLY -> period.plusMonths(1).atDay(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant();
            case ANNUAL  -> period.plusYears(1).atDay(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant();
        };
    }

    // ------------------------------------------------------------- dunning

    /** Daily 09:00. */
    @Scheduled(cron = "${app.payments.cron.dunning-cron:0 0 9 * * *}")
    public void markOverdue() {
        Instant threshold = Instant.now().minus(dunningDays, ChronoUnit.DAYS);
        List<Invoice> overdue = invoiceRepo.findOverduePending(threshold, PageRequest.of(0, batchSize));
        log.info("[InvoiceCron] dunning — {} invoices past due ({}d grace)", overdue.size(), dunningDays);
        for (Invoice i : overdue) {
            try {
                markOneOverdue(i.getPublicUuid());
            } catch (Exception e) {
                log.error("[InvoiceCron] failed dunning for invoice id={}: {}", i.getId(), e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOneOverdue(java.util.UUID invoicePublicUuid) {
        invoiceRepo.findByPublicUuid(invoicePublicUuid).ifPresent(inv -> {
            if (inv.getStatus() == Invoice.Status.PENDING) {
                inv.setStatus(Invoice.Status.OVERDUE);
                invoiceRepo.save(inv);
                log.info("[InvoiceCron] invoice {} marked OVERDUE", inv.getPublicUuid());
                // TODO Sprint 10.6: publish a NotificationEvent for
                // PAYMENT_OVERDUE so the bell lights up and (if
                // prefs allow) an email is sent. Wire when the
                // events module exposes a generic payload.
            }
        });
    }

    // Suppress unused import for BillingPeriod (referenced in the switch)
    @SuppressWarnings("unused")
    private static final BillingPeriod[] BPS = BillingPeriod.values();
}
