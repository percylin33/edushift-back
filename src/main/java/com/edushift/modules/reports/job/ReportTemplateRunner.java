package com.edushift.modules.reports.job;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportTemplate;
import com.edushift.modules.reports.repository.ReportJobRepository;
import com.edushift.modules.reports.repository.ReportTemplateRepository;
import com.edushift.modules.reports.service.ReportService;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Scheduler tick for {@link ReportTemplate} (Sprint cierre-C / B12).
 *
 * <p>Runs once per minute. Selects every active template whose
 * {@code next_run_at} has passed (cheap index lookup on
 * {@code idx_report_templates_due}), and for each:
 * <ol>
 *   <li>dispatches a {@link ReportJob} via {@link ReportService#request}; </li>
 *   <li>records {@code last_run_at} and the next {@code next_run_at} so
 *       the FE can show "próxima ejecución: ...". </li>
 * </ol>
 * </p>
 *
 * <p>Email delivery is intentionally best-effort: the runner never
 * fails a tick because of an SMTP outage. When SMTP is configured
 * (see {@code app.notifications.email.enabled} + {@code spring.mail.host}),
 * {@link com.edushift.modules.notifications.service.EmailSender} is
 * present in the context and the runner fires
 * {@code ReportEmailDispatcher} after the job completes. When SMTP
 * isn't configured, the runner still creates and runs the job, but
 * skips the email step and logs a WARN at startup.</p>
 *
 * <h3>Multi-tenant</h3>
 * The runner iterates templates one tenant at a time, asserting the
 * tenant context before each dispatch — defense-in-depth on top of
 * Hibernate's {@code @TenantId}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportTemplateRunner {

	private final ReportTemplateRepository templateRepository;
	private final ReportJobRepository jobRepository;
	private final ReportService reportService;

	@Autowired(required = false)
	private ReportEmailDispatcher emailDispatcher;

	@Value("${app.reports.scheduler.batch-size:50}")
	private int batchSize;

	@Value("${app.reports.scheduler.enabled:true}")
	private boolean enabled;

	/**
	 * Cron tick. {@code @Scheduled} drives this once per minute (Spring
	 * default for {@code cron = "* * * * * *"}).
	 *
	 * <p>Errors are caught per-template so one bad row doesn't poison the
	 * whole batch.</p>
	 */
	@Scheduled(cron = "0 * * * * *")
	public void tick() {
		if (!enabled) {
			return;
		}
		Instant now = Instant.now();
		List<ReportTemplate> due = templateRepository.findDueTemplates(now, PageRequest.of(0, batchSize));
		if (due.isEmpty()) {
			return;
		}
		log.info("[report-template-runner] tick: {} template(s) due", due.size());
		for (ReportTemplate tpl : due) {
			try {
				processOne(tpl);
			}
			catch (RuntimeException ex) {
				log.warn("[report-template-runner] template {} failed tick: {}",
						tpl.getPublicUuid(), ex.getMessage());
			}
		}
	}

	/**
	 * Public entry so the runner can be unit-tested with an arbitrary
	 * template list (no {@code @Scheduled} involvement).
	 */
	void processOne(ReportTemplate tpl) {
		TenantContext.runAs(tpl.getTenantId(), () -> {
			ReportJob job = reportService.request(
					SYSTEM_REQUESTOR, // system-initiated, audit row records actor=system
					ReportJob.ReportType.valueOf(tpl.getReportType().name()),
					ReportJob.Format.valueOf(tpl.getFormat().name()),
					tpl.getParams(),
					"template:" + tpl.getPublicUuid() + ":" + System.currentTimeMillis());
			job.setTriggeredByTemplate(tpl.getPublicUuid());
			jobRepository.save(job);
			log.info("[report-template-runner] dispatched job {} from template {}",
					job.getPublicUuid(), tpl.getPublicUuid());

			// Advance the schedule markers regardless of the job outcome.
			tpl.setLastRunAt(Instant.now());
			tpl.setNextRunAt(nextRunAfter(tpl));
			templateRepository.save(tpl);

			// Fire-and-forget email after the generator finishes.
			scheduleEmailDispatch(tpl, job);
			return null;
		});
	}

	/** Schedule the next run after the most recent tick (or after creation). */
	static Instant nextRunAfter(ReportTemplate tpl) {
		CronExpression cron;
		try {
			cron = CronExpression.parse(tpl.getCronExpression());
		}
		catch (RuntimeException e) {
			return null;
		}
		ZoneId zone;
		try {
			zone = ZoneId.of(tpl.getTimezone());
		}
		catch (RuntimeException e) {
			zone = ZoneId.of("America/Lima");
		}
		ZonedDateTime base = tpl.getLastRunAt() == null
				? ZonedDateTime.now(zone)
				: tpl.getLastRunAt().atZone(zone);
		ZonedDateTime next = cron.next(base);
		return next == null ? null : next.toInstant();
	}

	private void scheduleEmailDispatch(ReportTemplate tpl, ReportJob job) {
		if (emailDispatcher == null) {
			log.debug("[report-template-runner] email dispatcher not configured (app.notifications.email.enabled=false); "
					+ "skipping email for template {} job {}", tpl.getPublicUuid(), job.getPublicUuid());
			return;
		}
		if (tpl.getRecipients() == null || tpl.getRecipients().isEmpty()) {
			return;
		}
		// Submit to the executor; the dispatcher polls until the job
		// reaches DONE / FAILED then sends or logs the failure.
		emailDispatcher.enqueueAfterCompletion(tpl, job);
	}

	/** Sentinel UUID used as the {@code requested_by_user_id} for
	 * system-initiated template runs. Stored in the audit log so the
	 * job row is clearly attributed to the scheduler. */
	private static final UUID SYSTEM_REQUESTOR = UUID.fromString("00000000-0000-0000-0000-00000000beef");
}