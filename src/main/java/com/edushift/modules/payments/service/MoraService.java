package com.edushift.modules.payments.service;

import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.event.NotificationEvent;
import com.edushift.modules.payments.dto.PaymentPolicyDto;
import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.InvoiceItem;
import com.edushift.modules.payments.repository.InvoiceItemRepository;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoraService {

	private final InvoiceRepository invoiceRepository;
	private final InvoiceItemRepository itemRepository;
	private final PaymentPolicyService paymentPolicyService;
	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;
	private final StudentRepository studentRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Scheduled(cron = "${app.payments.cron.mora-cron:0 0 6 * * *}")
	public void applyMoraDaily() {
		List<Tenant> tenants = tenantRepository.findAll().stream()
				.filter(t -> !t.isDeleted())
				.toList();
		for (Tenant tenant : tenants) {
			try {
				TenantContext.runAs(tenant.getId(), () -> {
					applyForCurrentTenant();
					return null;
				});
			} catch (Exception ex) {
				log.warn("[mora] tenant {} failed: {}", tenant.getId(), ex.getMessage());
			}
		}
	}

	@Transactional
	public int applyForCurrentTenant() {
		PaymentPolicyDto policy = paymentPolicyService.getCurrent();
		if (!policy.moraEnabled() || policy.moraPercent() <= 0) {
			return 0;
		}
		Instant threshold = Instant.now().minus(policy.moraGraceDays(), ChronoUnit.DAYS);
		List<Invoice> candidates = new java.util.ArrayList<>(
				invoiceRepository.findOverduePending(threshold, PageRequest.of(0, 500)));
		candidates.addAll(invoiceRepository.findByStatusOrderByIssuedAtDesc(
						Invoice.Status.OVERDUE, PageRequest.of(0, 500))
				.getContent());
		int applied = 0;
		java.util.Set<UUID> seen = new java.util.HashSet<>();
		for (Invoice inv : candidates) {
			if (!seen.add(inv.getId())) {
				continue;
			}
			if (inv.getStatus() != Invoice.Status.OVERDUE && inv.getStatus() != Invoice.Status.PENDING) {
				continue;
			}
			if (inv.getDueAt() == null || inv.getDueAt().isAfter(threshold)) {
				continue;
			}
			if (inv.isMoraExempt() || inv.getMoraCents() > 0 || inv.getMoraAppliedAt() != null) {
				continue;
			}
			if (inv.getStatus() == Invoice.Status.PENDING) {
				inv.setStatus(Invoice.Status.OVERDUE);
			}
			long fee = Math.round(inv.getTotalCents() * (policy.moraPercent() / 100.0));
			if (fee <= 0) {
				continue;
			}
			inv.setMoraCents(fee);
			inv.setMoraAppliedAt(Instant.now());
			inv.setTotalCents(inv.getTotalCents() + fee);
			invoiceRepository.save(inv);

			InvoiceItem item = new InvoiceItem();
			item.setTenantId(inv.getTenantId());
			item.setInvoiceId(inv.getId());
			item.setDescription("Mora " + policy.moraPercent() + "%");
			item.setQuantity(1);
			item.setUnitAmountCents(fee);
			item.setLineTotalCents(fee);
			itemRepository.save(item);
			applied++;
		}
		log.info("[mora] applied {} fees for tenant={}", applied, TenantContext.currentRequired());
		return applied;
	}

	@Transactional
	public void exemptInvoice(UUID invoicePublicUuid) {
		Invoice inv = invoiceRepository.findByPublicUuid(invoicePublicUuid)
				.orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice not found"));
		inv.setMoraExempt(true);
		invoiceRepository.save(inv);
	}

	@Scheduled(cron = "${app.payments.cron.reminder-cron:0 30 8 * * *}")
	public void sendRemindersDaily() {
		List<Tenant> tenants = tenantRepository.findAll().stream()
				.filter(t -> !t.isDeleted())
				.toList();
		for (Tenant tenant : tenants) {
			try {
				TenantContext.runAs(tenant.getId(), () -> {
					sendRemindersForCurrentTenant();
					return null;
				});
			} catch (Exception ex) {
				log.warn("[reminder] tenant {} failed: {}", tenant.getId(), ex.getMessage());
			}
		}
	}

	@Transactional(readOnly = true)
	public int sendRemindersForCurrentTenant() {
		PaymentPolicyDto policy = paymentPolicyService.getCurrent();
		int sent = 0;
		Instant now = Instant.now();
		for (Integer daysBefore : policy.reminderDaysBeforeDue()) {
			if (daysBefore == null || daysBefore < 0) {
				continue;
			}
			Instant windowStart = now.plus(daysBefore, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
			Instant windowEnd = windowStart.plus(1, ChronoUnit.DAYS);
			List<Invoice> dueSoon = invoiceRepository.findPendingDueBetween(
					windowStart, windowEnd, PageRequest.of(0, 500));
			for (Invoice inv : dueSoon) {
				publishReminder(inv);
				sent++;
			}
		}
		return sent;
	}

	private void publishReminder(Invoice inv) {
		var guardian = userRepository.findByPublicUuid(inv.getGuardianUserId()).orElse(null);
		String parentName = guardian == null
				? ""
				: (guardian.getFirstName() + " " + guardian.getLastName()).trim();
		String studentName = studentRepository.findByPublicUuid(inv.getStudentId())
				.map(s -> s.fullName())
				.orElse("");
		String amount = String.format(Locale.US, "%s %.2f",
				inv.getCurrency() == null ? "PEN" : inv.getCurrency(),
				inv.getTotalCents() / 100.0);
		Map<String, Object> payload = new HashMap<>();
		payload.put("invoiceNumber", inv.getPeriodLabel());
		payload.put("parentName", parentName);
		payload.put("dueDate", inv.getDueAt() == null ? "" : inv.getDueAt().toString());
		payload.put("amount", amount);
		payload.put("studentName", studentName);
		payload.put("invoicePublicUuid", inv.getPublicUuid().toString());
		eventPublisher.publishEvent(
				NotificationEvent.builder()
						.templateKey("PAYMENT_REMINDER")
						.category(Notification.Category.PAYMENT)
						.sourceId(inv.getPublicUuid())
						.tenantId(inv.getTenantId())
						.recipients(List.of(new NotificationEvent.Recipient(
								inv.getGuardianUserId(),
								guardian == null ? null : guardian.getEmail())))
						.payload(payload)
						.build());
	}
}
