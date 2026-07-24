package com.edushift.modules.reports.job;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportTemplate;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Background dispatcher that sends the generated report by email once
 * the job reaches {@link ReportJob.Status#DONE} (Sprint cierre-C / B12).
 *
 * <p>Best-effort: any SMTP failure is logged but never blocks the
 * scheduler tick. The job row itself stays in DONE — the source of
 * truth for the tenant — and the operator can re-send manually from
 * the catalog UI.</p>
 *
 * <p>Only present when {@code app.notifications.email.enabled=true};
 * the runner silently skips when this bean is absent.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ReportEmailDispatcher {

	private final com.edushift.modules.notifications.service.EmailSender emailSender;

	@Autowired
	private com.edushift.modules.reports.service.ReportService reportService;

	@Value("${app.reports.email.poll-seconds:15}")
	private int pollSeconds;

	@Value("${app.reports.email.max-wait-minutes:15}")
	private int maxWaitMinutes;

	private final ConcurrentHashMap<java.util.UUID, Pending> pending = new ConcurrentHashMap<>();
	private ScheduledExecutorService executor;

	@PostConstruct
	void start() {
		executor = Executors.newScheduledThreadPool(1, r -> {
			Thread t = new Thread(r, "report-email-dispatcher");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Enqueue a job to be emailed once it completes. The dispatcher
	 * polls the BE until the job is DONE or FAILED, then calls
	 * {@link EmailSender} for each recipient.
	 *
	 * @param tpl the originating template (for subject + body)
	 * @param job the freshly-dispatched job to wait on
	 */
	public void enqueueAfterCompletion(ReportTemplate tpl, ReportJob job) {
		Pending p = new Pending(tpl, job.getPublicUuid());
		pending.put(job.getPublicUuid(), p);
		executor.scheduleWithFixedDelay(
				() -> pollOne(p),
				pollSeconds,
				pollSeconds,
				TimeUnit.SECONDS);
	}

	private void pollOne(Pending p) {
		ReportJob.Status status;
		try {
			status = reportService.getStatus(p.jobPublicUuid);
		}
		catch (RuntimeException e) {
			log.warn("[report-email] status poll failed for job {}: {}", p.jobPublicUuid, e.getMessage());
			return;
		}
		if (status == null || status == ReportJob.Status.PENDING || status == ReportJob.Status.RUNNING) {
			if (Instant.now().isAfter(p.deadline)) {
				log.warn("[report-email] job {} did not finish before deadline {} — abandoning",
						p.jobPublicUuid, p.deadline);
				pending.remove(p.jobPublicUuid);
			}
			return;
		}
		// Terminal status: send or skip, then evict.
		pending.remove(p.jobPublicUuid);
		if (status == ReportJob.Status.DONE) {
			String subject = p.tpl.getEmailSubject() != null
					? p.tpl.getEmailSubject()
					: "Reporte: " + p.tpl.getName();
			String body = p.tpl.getEmailBodyTemplate() != null
					? p.tpl.getEmailBodyTemplate()
					: "<p>Adjunto encontrás el reporte <strong>" + p.tpl.getName()
							+ "</strong>. Descargalo desde la consola de EduShift.</p>";
			for (String to : p.tpl.getRecipients()) {
				try {
					emailSender.send(to, subject, body);
				}
				catch (RuntimeException ex) {
					log.warn("[report-email] send to {} failed: {}", to, ex.getMessage());
				}
			}
			log.info("[report-email] sent template '{}' output to {} recipient(s)",
					p.tpl.getName(), p.tpl.getRecipients().size());
		}
		else {
			log.warn("[report-email] job {} ended in status {} — skipping email for template '{}'",
					p.jobPublicUuid, status, p.tpl.getName());
		}
	}

	private static final class Pending {
		final ReportTemplate tpl;
		final java.util.UUID jobPublicUuid;
		final Instant deadline;
		Pending(ReportTemplate tpl, java.util.UUID jobPublicUuid) {
			this.tpl = tpl;
			this.jobPublicUuid = jobPublicUuid;
			this.deadline = Instant.now().plus(Duration.ofMinutes(15));
		}
	}
}