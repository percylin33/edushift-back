package com.edushift.modules.payments.service;

import com.edushift.modules.payments.entity.Invoice;
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
	public Map<String, Long> agingBuckets() {
		TenantContext.currentRequired();
		Instant now = Instant.now();
		List<Invoice> overdue = invoiceRepository.findByStatusOrderByIssuedAtDesc(
						Invoice.Status.OVERDUE, PageRequest.of(0, 5000))
				.getContent();
		Map<String, Long> buckets = new LinkedHashMap<>();
		buckets.put("d0_30", 0L);
		buckets.put("d31_60", 0L);
		buckets.put("d61_90", 0L);
		buckets.put("d90_plus", 0L);
		for (Invoice i : overdue) {
			if (i.getDueAt() == null) {
				continue;
			}
			long days = ChronoUnit.DAYS.between(i.getDueAt(), now);
			String key;
			if (days <= 30) {
				key = "d0_30";
			} else if (days <= 60) {
				key = "d31_60";
			} else if (days <= 90) {
				key = "d61_90";
			} else {
				key = "d90_plus";
			}
			buckets.put(key, buckets.get(key) + i.getTotalCents());
		}
		return buckets;
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
