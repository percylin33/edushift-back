package com.edushift.modules.auth;

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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailerTest {

	@Mock private ObjectProvider<EmailSender> emailSender;
	@Mock private EmailLayoutRenderer layoutRenderer;
	@Mock private EmailBrandingResolver brandingResolver;
	@Mock private EmailSender sender;

	private PasswordResetMailer mailer;

	@BeforeEach
	void setUp() {
		mailer = new PasswordResetMailer(emailSender, layoutRenderer, brandingResolver);
	}

	@Test
	void returnsFalseWhenSmtpIsUnavailable() {
		when(emailSender.getIfAvailable()).thenReturn(null);

		assertThat(mailer.send("user@example.test", "Colegio", "https://example.test/reset", 30, "Ana"))
				.isFalse();
		verify(sender, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void sendsBrandedResetEmailAndUsesSafeDefaults() {
		when(emailSender.getIfAvailable()).thenReturn(sender);
		when(brandingResolver.current()).thenReturn(
				new EmailBrandingContext("EduShift", "https://cdn.example.test/logo.png", "#123456", "demo"));
		when(layoutRenderer.renderFragment(eq("password-reset.html"), anyMap())).thenReturn("reset fragment");
		when(layoutRenderer.wrap(anyString(), anyString(), any())).thenReturn("complete html");

		assertThat(mailer.send("user@example.test", "  ", "https://example.test/reset", 30, null)).isTrue();

		verify(layoutRenderer).renderFragment(eq("password-reset.html"), eq(Map.of(
				"schoolName", "EduShift",
				"resetUrl", "https://example.test/reset",
				"ttlMinutes", "30",
				"userFirstName", "usuario",
				"primaryColor", "#123456")));
		verify(layoutRenderer).wrap(eq("reset fragment"), eq("Restablece tu contraseña — EduShift"), any());
		verify(sender).send("user@example.test", "Restablece tu contraseña — EduShift", "complete html");
	}

	@Test
	void returnsFalseWhenSmtpSendFails() {
		when(emailSender.getIfAvailable()).thenReturn(sender);
		when(brandingResolver.current()).thenReturn(EmailBrandingContext.edushiftDefault(null));
		when(layoutRenderer.renderFragment(eq("password-reset.html"), anyMap())).thenReturn("fragment");
		when(layoutRenderer.wrap(anyString(), anyString(), any())).thenReturn("html");
		org.mockito.Mockito.doThrow(new IllegalStateException("SMTP unavailable"))
				.when(sender).send(anyString(), anyString(), anyString());

		assertThat(mailer.send("user@example.test", "Colegio", "https://example.test/reset", 15, "Ana"))
				.isFalse();
	}
}
