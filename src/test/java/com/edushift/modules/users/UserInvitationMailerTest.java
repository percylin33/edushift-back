package com.edushift.modules.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.notifications.email.EmailBrandingContext;
import com.edushift.modules.notifications.email.EmailBrandingResolver;
import com.edushift.modules.notifications.email.EmailLayoutRenderer;
import com.edushift.modules.notifications.service.EmailSender;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class UserInvitationMailerTest {

	@Mock private ObjectProvider<EmailSender> emailSender;
	@Mock private EmailLayoutRenderer layoutRenderer;
	@Mock private EmailBrandingResolver brandingResolver;
	@Mock private EmailSender sender;

	private UserInvitationMailer mailer;

	@BeforeEach
	void setUp() {
		mailer = new UserInvitationMailer(emailSender, layoutRenderer, brandingResolver);
	}

	@Test
	void skipsSyntheticAddressesBeforeLookingUpSmtp() {
		assertThat(mailer.send(" ana.lopez.123456.abcdef@school.com ", "Colegio",
				"https://example.test/invite", Instant.now())).isFalse();
		verify(emailSender, never()).getIfAvailable();
	}

	@Test
	void returnsFalseWhenSmtpIsUnavailable() {
		when(emailSender.getIfAvailable()).thenReturn(null);

		assertThat(mailer.send("ana@example.test", "Colegio", "https://example.test/invite", Instant.now()))
				.isFalse();
	}

	@Test
	void sendsParentInvitationWithTenantBrandingAndExpiry() {
		when(emailSender.getIfAvailable()).thenReturn(sender);
		when(brandingResolver.current()).thenReturn(
				new EmailBrandingContext("EduShift", null, "#123456", "demo"));
		when(layoutRenderer.renderFragment(eq("invitation-user.html"), anyMap())).thenReturn("invitation fragment");
		when(layoutRenderer.wrap(anyString(), anyString(), any())).thenReturn("complete html");
		Instant expiresAt = Instant.parse("2026-10-29T15:00:00Z");

		assertThat(mailer.send("ana@example.test", "Colegio Norte", "https://example.test/accept", expiresAt,
				Set.of("TEACHER", "PARENT"))).isTrue();

		verify(layoutRenderer).renderFragment(eq("invitation-user.html"), eq(Map.of(
				"heading", "Activa tu cuenta de familia",
				"body", "Te invitaron a unirte a <strong>Colegio Norte</strong> como tutor. Entra al enlace, "
						+ "crea tu contraseña y podrás ver a tus hijos (asistencia, notas y pagos).",
				"acceptUrl", "https://example.test/accept",
				"expiry", "29/10/2026 10:00 (hora de Lima)",
				"primaryColor", "#123456")));
		verify(layoutRenderer).wrap(eq("invitation fragment"), eq("Activa tu cuenta de familia en Colegio Norte"), any());
		verify(sender).send("ana@example.test", "Activa tu cuenta de familia en Colegio Norte", "complete html");
	}

	@Test
	void defaultsToStaffCopyWhenRolesAreEmptyAndExpiryIsMissing() {
		when(emailSender.getIfAvailable()).thenReturn(sender);
		when(brandingResolver.current()).thenReturn(EmailBrandingContext.edushiftDefault(null));
		when(layoutRenderer.renderFragment(eq("invitation-user.html"), anyMap())).thenReturn("fragment");
		when(layoutRenderer.wrap(anyString(), anyString(), any())).thenReturn("html");

		assertThat(mailer.send("staff@example.test", null, "https://example.test/accept", null, List.of())).isTrue();

		verify(layoutRenderer).renderFragment(eq("invitation-user.html"), eq(Map.of(
				"heading", "Activa tu cuenta",
				"body", "Te invitaron a unirte a <strong>EduShift</strong> en EduShift. Entra al enlace, "
						+ "crea tu contraseña y ya podrás iniciar sesión en el colegio.",
				"acceptUrl", "https://example.test/accept",
				"expiry", "7 días",
				"primaryColor", EmailBrandingContext.DEFAULT_PRIMARY)));
		verify(sender).send("staff@example.test", "Activa tu cuenta en EduShift", "html");
	}

	@Test
	void catchesMailTransportFailures() {
		when(emailSender.getIfAvailable()).thenReturn(sender);
		when(brandingResolver.current()).thenReturn(EmailBrandingContext.edushiftDefault(null));
		when(layoutRenderer.renderFragment(eq("invitation-user.html"), anyMap())).thenReturn("fragment");
		when(layoutRenderer.wrap(anyString(), anyString(), any())).thenReturn("html");
		org.mockito.Mockito.doThrow(new IllegalStateException("SMTP unavailable"))
				.when(sender).send(anyString(), anyString(), anyString());

		assertThat(mailer.send("staff@example.test", "Colegio", "https://example.test/accept", Instant.now()))
				.isFalse();
	}
}
