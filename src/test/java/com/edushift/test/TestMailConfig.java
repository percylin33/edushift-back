package com.edushift.test;

import java.io.InputStream;
import java.util.Properties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Stub {@link JavaMailSender} for integration tests (Sprint 11 / BE-11.7).
 *
 * <p>{@code IntegrationTest} imports this config so {@code EmailSender}
 * (which is gated on {@code app.notifications.email.enabled=true}) can be
 * wired in tests that exercise the email pipeline. The bean is a no-op —
 * every send call is silently dropped — keeping the test environment
 * hermetic (no SMTP required).</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfig {

    @Bean
    @Primary
    JavaMailSender testJavaMailSender() {
        return new NoopJavaMailSender();
    }

    /**
     * In-memory {@link JavaMailSender} that swallows every send. Carries
     * the {@code @Primary} marker so it wins over any auto-configured
     * bean Spring tries to create from {@code spring.mail.host}.
     */
    static final class NoopJavaMailSender implements JavaMailSender {

        private final Session session = Session.getInstance(new Properties());

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(session);
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(session, contentStream);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read mime content", e);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            // no-op
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            // no-op
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            // no-op
        }

        @Override
        public void send(MimeMessagePreparator[] mimeMessagePreparators) throws MailException {
            // no-op
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            // no-op
        }

        @Override
        public void send(SimpleMailMessage[] simpleMessages) throws MailException {
            // no-op
        }
    }
}
