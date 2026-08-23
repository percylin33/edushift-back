package com.edushift.modules.notifications.email;

import com.edushift.modules.notifications.service.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs whether transactional email (SMTP) is active at startup.
 * Helps catch the common dev mistake of running {@code mvnw spring-boot:run}
 * without loading {@code .env} first (see {@code run-dev.ps1}).
 */
@Component
@Slf4j
public class EmailModuleStartupLogger {

	private final ObjectProvider<EmailSender> emailSender;
	private final boolean emailEnabled;
	private final String mailHost;

	public EmailModuleStartupLogger(
			ObjectProvider<EmailSender> emailSender,
			@Value("${app.notifications.email.enabled:false}") boolean emailEnabled,
			@Value("${spring.mail.host:}") String mailHost
	) {
		this.emailSender = emailSender;
		this.emailEnabled = emailEnabled;
		this.mailHost = mailHost == null ? "" : mailHost.trim();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		boolean senderBean = emailSender.getIfAvailable() != null;
		boolean hostConfigured = !mailHost.isBlank();

		if (emailEnabled && hostConfigured && senderBean) {
			log.info("[email] SMTP ACTIVE — host={}, from module flag enabled=true", mailHost);
		}
		else {
			log.warn("[email] SMTP INACTIVE — app.notifications.email.enabled={}, spring.mail.host={}, "
							+ "EmailSender bean={}. Password-reset and invitation emails will NOT send. "
							+ "Use .\\run-dev.ps1 (loads .env) or export APP_NOTIFICATIONS_EMAIL_ENABLED + SPRING_MAIL_*.",
					emailEnabled, hostConfigured ? mailHost : "(empty)", senderBean);
		}
	}
}
