package com.edushift.modules.users;

import com.edushift.modules.notifications.email.EmailBrandingContext;
import com.edushift.modules.notifications.email.EmailBrandingResolver;
import com.edushift.modules.notifications.email.EmailLayoutRenderer;
import com.edushift.modules.notifications.service.EmailSender;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Emails the school-scoped invitation link when SMTP is enabled.
 * The admin still gets the token in the API response to copy manually.
 */
@Component
@Slf4j
public class UserInvitationMailer {

	private static final DateTimeFormatter EXPIRY =
			DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));

	private final ObjectProvider<EmailSender> emailSender;
	private final EmailLayoutRenderer layoutRenderer;
	private final EmailBrandingResolver brandingResolver;

	public UserInvitationMailer(
			ObjectProvider<EmailSender> emailSender,
			EmailLayoutRenderer layoutRenderer,
			EmailBrandingResolver brandingResolver
	) {
		this.emailSender = emailSender;
		this.layoutRenderer = layoutRenderer;
		this.brandingResolver = brandingResolver;
	}

	public boolean send(String to, String schoolName, String acceptUrl, Instant expiresAt) {
		return send(to, schoolName, acceptUrl, expiresAt, Set.of());
	}

	public boolean send(String to, String schoolName, String acceptUrl, Instant expiresAt,
			Collection<String> roles) {
		EmailSender sender = emailSender.getIfAvailable();
		if (sender == null) {
			log.info("[invitations] SMTP disabled — copy link for {} → {}", to, acceptUrl);
			return false;
		}
		Audience audience = Audience.fromRoles(roles);
		try {
			sender.send(to, audience.subject(schoolName),
					html(schoolName, acceptUrl, expiresAt, audience));
			return true;
		} catch (RuntimeException ex) {
			log.warn("[invitations] email failed to={} — admin can copy the link", to, ex);
			return false;
		}
	}

	private String html(String schoolName, String acceptUrl, Instant expiresAt,
			Audience audience) {
		String school = schoolName == null || schoolName.isBlank() ? "EduShift" : schoolName;
		String expiry = expiresAt == null ? "7 días" : EXPIRY.format(expiresAt) + " (hora de Lima)";
		EmailBrandingContext branding = brandingResolver.current();
		if (schoolName != null && !schoolName.isBlank()) {
			branding = new EmailBrandingContext(
					schoolName,
					branding.logoUrl(),
					branding.primaryOrDefault(),
					branding.slug());
		}
		String fragment = layoutRenderer.renderFragment("invitation-user.html", Map.of(
				"heading", audience.heading(),
				"body", audience.body(school),
				"acceptUrl", acceptUrl,
				"expiry", expiry,
				"primaryColor", branding.primaryOrDefault()));
		return layoutRenderer.wrap(fragment, audience.subject(school), branding);
	}

	private enum Audience {
		PARENT,
		STUDENT,
		STAFF;

		static Audience fromRoles(Collection<String> roles) {
			if (roles == null || roles.isEmpty()) {
				return STAFF;
			}
			for (String role : roles) {
				if (role == null) {
					continue;
				}
				String upper = role.toUpperCase(Locale.ROOT);
				if ("PARENT".equals(upper)) {
					return PARENT;
				}
				if ("STUDENT".equals(upper)) {
					return STUDENT;
				}
			}
			return STAFF;
		}

		String subject(String schoolName) {
			String school = schoolName == null || schoolName.isBlank() ? "EduShift" : schoolName;
			return switch (this) {
				case PARENT -> "Activa tu cuenta de familia en " + school;
				case STUDENT -> "Activa tu cuenta de estudiante en " + school;
				case STAFF -> "Activa tu cuenta en " + school;
			};
		}

		String heading() {
			return switch (this) {
				case PARENT -> "Activa tu cuenta de familia";
				case STUDENT -> "Activa tu cuenta de estudiante";
				case STAFF -> "Activa tu cuenta";
			};
		}

		String body(String school) {
			return switch (this) {
				case PARENT -> "Te invitaron a unirte a <strong>" + school
						+ "</strong> como tutor. Entra al enlace, crea tu contraseña "
						+ "y podrás ver a tus hijos (asistencia, notas y pagos).";
				case STUDENT -> "Te invitaron a unirte a <strong>" + school
						+ "</strong> como estudiante. Entra al enlace, crea tu contraseña "
						+ "y ya podrás ver tus cursos, notas y horario.";
				case STAFF -> "Te invitaron a unirte a <strong>" + school
						+ "</strong> en EduShift. Entra al enlace, crea tu contraseña "
						+ "y ya podrás iniciar sesión en el colegio.";
			};
		}
	}
}
