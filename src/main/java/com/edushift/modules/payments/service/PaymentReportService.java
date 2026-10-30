package com.edushift.modules.payments.service;

import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.dto.PaymentAgingReportDto;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentReportService {

	private final InvoiceRepository invoiceRepository;

	@Transactional(readOnly = true)
	public String exportCsv() {
		TenantContext.currentRequired();
		List<Invoice> all = invoiceRepository.findAllByOrderByIssuedAtDesc(PageRequest.of(0, 5000))
				.getContent();
		StringJoiner sj = new StringJoiner("\n");
		sj.add("publicUuid,periodLabel,studentId,guardianUserId,status,totalCents,currency,dueAt,paidAt,moraCents");
		for (Invoice i : all) {
			sj.add(String.join(",",
					safe(i.getPublicUuid()),
					csv(i.getPeriodLabel()),
					safe(i.getStudentId()),
					safe(i.getGuardianUserId()),
					safe(i.getStatus()),
					String.valueOf(i.getTotalCents()),
					csv(i.getCurrency()),
					safe(i.getDueAt()),
					safe(i.getPaidAt()),
					String.valueOf(i.getMoraCents())));
		}
		return sj.toString();
	}

	@Transactional(readOnly = true)
	public PaymentAgingReportDto agingBuckets() {
		var tenantId = TenantContext.currentRequired();
		Instant now = Instant.now();
		List<InvoiceRepository.AgingBucketProjection> aggregates = invoiceRepository.aggregateOverdueByAge(
				tenantId,
				now,
				now.minus(30, ChronoUnit.DAYS),
				now.minus(60, ChronoUnit.DAYS),
				now.minus(90, ChronoUnit.DAYS));
		Map<String, PaymentAgingReportDto.AgingBucket> buckets = new LinkedHashMap<>();
		buckets.put("d0_30", emptyBucket());
		buckets.put("d31_60", emptyBucket());
		buckets.put("d61_90", emptyBucket());
		buckets.put("d90_plus", emptyBucket());
		long totalCents = 0;
		long invoiceCount = 0;
		for (InvoiceRepository.AgingBucketProjection row : aggregates) {
			PaymentAgingReportDto.AgingBucket bucket = new PaymentAgingReportDto.AgingBucket(
					value(row.getAmountCents()), value(row.getInvoiceCount()), value(row.getStudentCount()));
			buckets.put(row.getBucket(), bucket);
			totalCents += bucket.amountCents();
			invoiceCount += bucket.invoiceCount();
		}
		return new PaymentAgingReportDto(
				totalCents,
				invoiceCount,
				invoiceRepository.countOverdueStudents(tenantId, now),
				buckets.get("d0_30"),
				buckets.get("d31_60"),
				buckets.get("d61_90"),
				buckets.get("d90_plus"));
	}

	private static PaymentAgingReportDto.AgingBucket emptyBucket() {
		return new PaymentAgingReportDto.AgingBucket(0, 0, 0);
	}

	private static long value(Long value) {
		return value == null ? 0 : value;
	}

	private static String csv(String s) {
		if (s == null) {
			return "";
		}
		String escaped = s.replace("\"", "\"\"");
		if (escaped.contains(",") || escaped.contains("\n")) {
			return "\"" + escaped + "\"";
		}
		return escaped;
	}

	private static String safe(Object o) {
		return o == null ? "" : o.toString();
	}
}
