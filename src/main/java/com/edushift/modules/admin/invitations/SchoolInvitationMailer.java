package com.edushift.modules.admin.invitations;

import com.edushift.modules.notifications.email.EmailBrandingContext;
import com.edushift.modules.notifications.email.EmailLayoutRenderer;
import com.edushift.modules.notifications.service.EmailSender;
import com.edushift.shared.web.FrontendLinks;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Builds the founder setup URL and sends the invitation email when SMTP
 * is enabled. When {@link EmailSender} is absent the admin still gets the
 * link in the API response.
 */
@Component
@Slf4j
public class SchoolInvitationMailer {

	private static final DateTimeFormatter EXPIRY =
			DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));

	private final ObjectProvider<EmailSender> emailSender;
	private final FrontendLinks frontendLinks;
	private final EmailLayoutRenderer layoutRenderer;

	public SchoolInvitationMailer(
			ObjectProvider<EmailSender> emailSender,
			FrontendLinks frontendLinks,
			EmailLayoutRenderer layoutRenderer
	) {
		this.emailSender = emailSender;
		this.frontendLinks = frontendLinks;
		this.layoutRenderer = layoutRenderer;
	}

	public String setupUrl(String token) {
		return frontendLinks.schoolSetup(token);
	}

	public boolean send(String to, String setupUrl, Instant expiresAt) {
		EmailSender sender = emailSender.getIfAvailable();
		if (sender == null) {
			log.info("[school-invites] SMTP disabled — copy link for {} → {}", to, setupUrl);
			return false;
		}
		try {
			sender.send(to, "Invitación para configurar tu colegio en EduShift",
					html(setupUrl, expiresAt));
			return true;
		} catch (RuntimeException ex) {
			log.warn("[school-invites] email failed to={} — admin can copy the link", to, ex);
			return false;
		}
	}

	private String html(String setupUrl, Instant expiresAt) {
		String expiry = expiresAt == null ? "7 días" : EXPIRY.format(expiresAt) + " (hora de Lima)";
		EmailBrandingContext branding = EmailBrandingContext.edushiftDefault(null);
		String fragment = layoutRenderer.renderFragment("invitation-school.html", Map.of(
				"setupUrl", setupUrl,
				"expiry", expiry,
				"primaryColor", branding.primaryOrDefault()));
		return layoutRenderer.wrap(fragment,
				"Configura tu colegio en EduShift",
				branding);
	}
}
