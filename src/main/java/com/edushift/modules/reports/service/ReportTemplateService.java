package com.edushift.modules.reports.service;

import com.edushift.modules.reports.dto.ReportTemplateRequest;
import com.edushift.modules.reports.dto.ReportTemplateResponse;
import com.edushift.modules.reports.entity.ReportTemplate;
import com.edushift.modules.reports.exception.BadCronExpressionException;
import com.edushift.modules.reports.repository.ReportTemplateRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped CRUD for {@link ReportTemplate} (Sprint cierre-C / B12).
 *
 * <p>Computes {@code next_run_at} on every save using Spring's
 * {@link CronExpression} — the FE shows it as UI feedback ("próxima
 * ejecución: 2026-08-01 08:00"). The actual scheduler tick lives in
 * {@link ReportTemplateRunner}; this service only owns the CRUD
 * surface.</p>
 *
 * <p>Multi-tenant safety: all reads/writes go through the tenant-scoped
 * repository. Cross-tenant lookups by {@code publicUuid} return
 * {@link ResourceNotFoundException} (404, anti-enumeration).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTemplateService {

	private final ReportTemplateRepository repository;

	@Transactional(readOnly = true)
	public Page<ReportTemplateResponse> list(Pageable pageable) {
		TenantContext.currentRequired();
		return repository
				.findByDeletedFalseOrderByCreatedAtDesc(pageable)
				.map(ReportTemplateResponse::from);
	}

	@Transactional(readOnly = true)
	public ReportTemplateResponse get(UUID publicUuid) {
		return ReportTemplateResponse.from(mustFind(publicUuid));
	}

	@Transactional
	public ReportTemplateResponse create(ReportTemplateRequest req) {
		TenantContext.currentRequired();
		if (repository.existsByNameAndDeletedFalse(req.name())) {
			throw new com.edushift.shared.exception.BusinessException(
					"REPORT_TEMPLATE_NAME_EXISTS",
					"A template with name '" + req.name() + "' already exists in this tenant");
		}
		ReportTemplate t = new ReportTemplate();
		applyRequest(t, req);
		t = repository.saveAndFlush(t);
		log.info("[report-template] created publicUuid={} cron='{}' tz={}",
				t.getPublicUuid(), t.getCronExpression(), t.getTimezone());
		return ReportTemplateResponse.from(t);
	}

	@Transactional
	public ReportTemplateResponse update(UUID publicUuid, ReportTemplateRequest req) {
		ReportTemplate t = mustFind(publicUuid);
		applyRequest(t, req);
		t = repository.save(t);
		log.info("[report-template] updated publicUuid={}", t.getPublicUuid());
		return ReportTemplateResponse.from(t);
	}

	@Transactional
	public void softDelete(UUID publicUuid) {
		ReportTemplate t = mustFind(publicUuid);
		t.markDeleted();
		repository.save(t);
		log.info("[report-template] soft-deleted publicUuid={}", t.getPublicUuid());
	}

	private ReportTemplate mustFind(UUID publicUuid) {
		return repository.findByPublicUuidAndDeletedFalse(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("ReportTemplate", publicUuid));
	}

	private static void applyRequest(ReportTemplate t, ReportTemplateRequest r) {
		// Validate cron up front so we don't persist an unparseable row.
		CronExpression cron = parseCron(r.cronExpression());
		ZoneId zone = parseZone(r.timezone());
		t.setName(r.name());
		t.setDescription(r.description());
		t.setReportType(r.reportType());
		t.setFormat(r.format());
		t.setActive(r.active() == null ? Boolean.TRUE : r.active());
		t.setCronExpression(r.cronExpression());
		t.setTimezone(r.timezone());
		t.setRecipients(r.recipients() == null ? new ArrayList<>() : new ArrayList<>(r.recipients()));
		t.setEmailSubject(r.emailSubject());
		t.setEmailBodyTemplate(r.emailBodyTemplate());
		t.setParams(r.params() == null ? "{}" : r.params());
		// Pre-compute next_run_at so the FE can show "próxima: 2026-08-01 08:00".
		ZonedDateTime now = ZonedDateTime.now(zone);
		ZonedDateTime next = cron.next(now);
		t.setNextRunAt(next == null ? null : next.toInstant());
	}

	static CronExpression parseCron(String cron) {
		try {
			return CronExpression.parse(cron);
		}
		catch (RuntimeException e) {
			throw new BadCronExpressionException(cron);
		}
	}

	static ZoneId parseZone(String tz) {
		try {
			return ZoneId.of(tz);
		}
		catch (RuntimeException e) {
			log.warn("[report-template] invalid timezone '{}' — falling back to America/Lima", tz);
			return ZoneId.of("America/Lima");
		}
	}
}