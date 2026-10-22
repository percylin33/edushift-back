package com.edushift.modules.payments.dto;

/** Aggregated tenant-scoped summary of overdue invoices. Amounts are in cents. */
public record PaymentAgingReportDto(
		long totalOverdueCents,
		long totalOverdueInvoices,
		long totalOverdueStudents,
		AgingBucket d0_30,
		AgingBucket d31_60,
		AgingBucket d61_90,
		AgingBucket d90_plus
) {
	public record AgingBucket(long amountCents, long invoiceCount, long studentCount) {}
}
