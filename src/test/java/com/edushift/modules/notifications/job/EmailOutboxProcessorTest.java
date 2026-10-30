package com.edushift.modules.notifications.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.notifications.email.EmailBrandingContext;
import com.edushift.modules.notifications.email.EmailBrandingResolver;
import com.edushift.modules.notifications.entity.EmailOutbox;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.repository.EmailOutboxRepository;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.notifications.service.EmailSender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailOutboxProcessorTest {

	@Mock EmailOutboxRepository outboxRepo;
	@Mock NotificationRepository notificationRepo;
	@Mock EmailSender emailSender;
	@Mock EmailBrandingResolver brandingResolver;

	private EmailOutboxProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new EmailOutboxProcessor(outboxRepo, notificationRepo, emailSender, brandingResolver);
		ReflectionTestUtils.setField(processor, "batchSize", 50);
	}

	@Test
	void processLoadsNotificationsAndBrandingOnceForTheBatch() {
		UUID tenantId = UUID.randomUUID();
		List<EmailOutbox> rows = List.of(row(tenantId), row(tenantId), row(tenantId));
		List<Notification> notifications = rows.stream().map(this::notificationFor).toList();
		when(outboxRepo.pickPending(any(), anyInt())).thenReturn(rows);
		when(outboxRepo.findById(any())).thenAnswer(inv -> rows.stream()
				.filter(row -> row.getId().equals(inv.getArgument(0)))
				.findFirst());
		when(notificationRepo.findAllById(any())).thenReturn(notifications);
		when(brandingResolver.forTenantId(tenantId))
				.thenReturn(EmailBrandingContext.edushiftDefault(null));

		processor.process();

		verify(notificationRepo, times(1)).findAllById(any());
		verify(notificationRepo, never()).findById(any());
		verify(brandingResolver, times(1)).forTenantId(tenantId);
		verify(outboxRepo, times(3)).findById(any());
		verify(emailSender, times(3)).send(any(), any(), any(), any(), any(), any());
	}

	private EmailOutbox row(UUID tenantId) {
		EmailOutbox row = new EmailOutbox();
		row.setId(UUID.randomUUID());
		row.setTenantId(tenantId);
		row.setNotificationId(UUID.randomUUID());
		row.setToEmail("parent@school.test");
		row.setSubject("Aviso");
		row.setBodyHtml("<p>hola</p>");
		return row;
	}

	private Notification notificationFor(EmailOutbox row) {
		Notification notification = new Notification();
		notification.setId(row.getNotificationId());
		notification.setRecipientUserId(UUID.randomUUID());
		notification.setChannel(Channel.EMAIL);
		notification.setCategory(Category.QUIZ);
		return notification;
	}
}
