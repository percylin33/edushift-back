package com.edushift.modules.auth;

import com.edushift.modules.notifications.email.EmailBrandingContext;
import com.edushift.modules.notifications.email.EmailBrandingResolver;
import com.edushift.modules.notifications.email.EmailLayoutRenderer;
import com.edushift.modules.notifications.service.EmailSender;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Sends the school-scoped password-reset link when SMTP is enabled.
 * Mirrors {@link com.edushift.modules.users.UserInvitationMailer}: direct
 * {@link EmailSender} instead of the notification outbox (faster, works with
 * the same {@code APP_NOTIFICATIONS_EMAIL_ENABLED + SPRING_MAIL_*} flags).
 */
@Component
@Slf4j
public class PasswordResetMailer {

	private final ObjectProvider<EmailSender> emailSender;
	private final EmailLayoutRenderer layoutRenderer;
	private final EmailBrandingResolver brandingResolver;

	public PasswordResetMailer(
			ObjectProvider<EmailSender> emailSender,
			EmailLayoutRenderer layoutRenderer,
			EmailBrandingResolver brandingResolver
	) {
		this.emailSender = emailSender;
		this.layoutRenderer = layoutRenderer;
		this.brandingResolver = brandingResolver;
	}

	public boolean send(String to, String schoolName, String resetUrl, long ttlMinutes, String userFirstName) {
		EmailSender sender = emailSender.getIfAvailable();
		if (sender == null) {
			log.info("[auth] SMTP disabled — copy reset link for {} → {}", to, resetUrl);
			return false;
		}
		String school = schoolName == null || schoolName.isBlank() ? "EduShift" : schoolName;
		String firstName = userFirstName == null || userFirstName.isBlank() ? "usuario" : userFirstName;
		try {
			sender.send(to, subject(school), html(school, resetUrl, ttlMinutes, firstName));
			return true;
		}
		catch (RuntimeException ex) {
			log.warn("[auth] password-reset email failed to={} — user can use in-app link", to, ex);
			return false;
		}
	}

	private String subject(String schoolName) {
		return "Restablece tu contraseña — " + schoolName;
	}

	private String html(String schoolName, String resetUrl, long ttlMinutes, String userFirstName) {
		EmailBrandingContext branding = brandingResolver.current();
		if (schoolName != null && !schoolName.isBlank()) {
			branding = new EmailBrandingContext(
					schoolName,
					branding.logoUrl(),
					branding.primaryOrDefault(),
					branding.slug());
		}
		String fragment = layoutRenderer.renderFragment("password-reset.html", Map.of(
				"schoolName", schoolName,
				"resetUrl", resetUrl,
				"ttlMinutes", String.valueOf(ttlMinutes),
				"userFirstName", userFirstName,
				"primaryColor", branding.primaryOrDefault()));
		return layoutRenderer.wrap(fragment, subject(schoolName), branding);
	}
}
