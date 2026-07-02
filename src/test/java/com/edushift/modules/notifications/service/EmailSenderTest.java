package com.edushift.modules.notifications.service;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.edushift.modules.ai.safety.PiiSafetyFilter;
import com.edushift.modules.notifications.security.UnsubscribeTokenSigner;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
@ExtendWith(MockitoExtension.class)
class EmailSenderTest {
    @Mock JavaMailSender mailSender; @Mock PiiSafetyFilter piiFilter; @Mock UnsubscribeTokenSigner unsubscribeSigner;
    @InjectMocks EmailSender emailSender;
    @Mock MimeMessage mimeMessage;
    @Test void sendSimple() { when(mailSender.createMimeMessage()).thenReturn(mimeMessage); when(piiFilter.mask(any())).thenAnswer(i -> i.getArgument(0)); ReflectionTestUtils.setField(emailSender, "enabled", true); emailSender.send("a@t", "Subj", "Body"); verify(mailSender).send(any(MimeMessage.class)); }
}
