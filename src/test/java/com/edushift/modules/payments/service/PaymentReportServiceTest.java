package com.edushift.modules.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.payments.dto.PaymentAgingReportDto;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PaymentReportServiceTest {

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final PaymentReportService service = new PaymentReportService(invoiceRepository);
    private final UUID tenantId = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void agingBuckets_returnsTotalsAndZeroFilledRangesForCurrentTenant() {
        TenantContext.set(tenantId);
        InvoiceRepository.AgingBucketProjection recent = row("d0_30", 120_000L, 3L, 2L);
        InvoiceRepository.AgingBucketProjection critical = row("d90_plus", 45_000L, 1L, 1L);
        when(invoiceRepository.aggregateOverdueByAge(eq(tenantId), any(), any(), any(), any()))
                .thenReturn(List.of(recent, critical));
        when(invoiceRepository.countOverdueStudents(eq(tenantId), any())).thenReturn(2L);

        PaymentAgingReportDto report = service.agingBuckets();

        assertThat(report.totalOverdueCents()).isEqualTo(165_000L);
        assertThat(report.totalOverdueInvoices()).isEqualTo(4L);
        assertThat(report.totalOverdueStudents()).isEqualTo(2L);
        assertThat(report.d0_30()).isEqualTo(new PaymentAgingReportDto.AgingBucket(120_000L, 3L, 2L));
        assertThat(report.d31_60()).isEqualTo(new PaymentAgingReportDto.AgingBucket(0L, 0L, 0L));
        assertThat(report.d61_90()).isEqualTo(new PaymentAgingReportDto.AgingBucket(0L, 0L, 0L));
        assertThat(report.d90_plus()).isEqualTo(new PaymentAgingReportDto.AgingBucket(45_000L, 1L, 1L));
        verify(invoiceRepository).aggregateOverdueByAge(eq(tenantId), any(), any(), any(), any());
    }

    private static InvoiceRepository.AgingBucketProjection row(
            String bucket, Long amountCents, Long invoiceCount, Long studentCount) {
        InvoiceRepository.AgingBucketProjection projection = mock(InvoiceRepository.AgingBucketProjection.class);
        when(projection.getBucket()).thenReturn(bucket);
        when(projection.getAmountCents()).thenReturn(amountCents);
        when(projection.getInvoiceCount()).thenReturn(invoiceCount);
        when(projection.getStudentCount()).thenReturn(studentCount);
        return projection;
    }
}
