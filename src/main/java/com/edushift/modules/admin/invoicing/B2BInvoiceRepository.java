package com.edushift.modules.admin.invoicing;

import com.edushift.modules.admin.invoicing.B2BInvoice.B2BInvoiceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface B2BInvoiceRepository extends JpaRepository<B2BInvoice, UUID> {

    List<B2BInvoice> findByTenantIdOrderByIssuedAtDesc(UUID tenantId);

    Page<B2BInvoice> findByStatus(B2BInvoiceStatus status, Pageable pageable);

    Page<B2BInvoice> findByTenantId(UUID tenantId, Pageable pageable);

    Page<B2BInvoice> findByStatusAndTenantId(B2BInvoiceStatus status, UUID tenantId, Pageable pageable);

    Optional<B2BInvoice> findBySubscriptionIdAndPeriodStartAndPeriodEnd(
            UUID subscriptionId, LocalDate periodStart, LocalDate periodEnd);

    List<B2BInvoice> findByStatusAndDueAtBefore(B2BInvoiceStatus status, LocalDate date);

    List<B2BInvoice> findByStatus(B2BInvoiceStatus status);

    List<B2BInvoice> findByIssuedAtBetween(Instant start, Instant end);
}
