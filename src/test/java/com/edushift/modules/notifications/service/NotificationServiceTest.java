package com.edushift.modules.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.edushift.modules.notifications.repository.EmailOutboxRepository;
import com.edushift.modules.notifications.repository.NotificationPreferenceRepository;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.notifications.repository.NotificationTemplateRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock NotificationRepository notificationRepo;
	@Mock NotificationTemplateRepository templateRepo;
	@Mock NotificationPreferenceRepository preferenceRepo;
	@Mock EmailOutboxRepository outboxRepo;
	@Mock NotificationTemplateEngine engine;
	@Mock com.fasterxml.jackson.databind.ObjectMapper objectMapper;
	@InjectMocks NotificationService service;

	@BeforeEach
	void setUp() {
		TenantContext.set(UUID.randomUUID());
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Test
	void countUnread() {
		when(notificationRepo.countUnreadByRecipient(any())).thenReturn(3L);
		assertThat(service.countUnread(UUID.randomUUID())).isEqualTo(3L);
	}

	@Test
	void notifySkipsOutboxWhenEmailDisabled() throws Exception {
		ReflectionTestUtils.setField(service, "emailEnabled", false);
		UUID userId = UUID.randomUUID();
		NotificationTemplate template = new NotificationTemplate();
		template.setTemplateKey("PASSWORD_RESET");
		template.setSubject("Reset");
		template.setBodyHtml("<p>hi</p>");
		when(templateRepo.findByKeyAndLocale("PASSWORD_RESET", "es-PE")).thenReturn(Optional.of(template));
		when(preferenceRepo.findByUserIdAndChannelAndCategory(any(), any(), any()))
				.thenReturn(Optional.empty());
		when(engine.render(any(), any())).thenReturn(
				new NotificationTemplateEngine.Rendered("Reset", "<p>hi</p>"));
		when(notificationRepo.save(any(Notification.class))).thenAnswer(inv -> {
			Notification n = inv.getArgument(0);
			n.setPublicUuid(UUID.randomUUID());
			return n;
		});

		Optional<UUID> result = service.notify(NotificationService.NotifyCommand.builder()
				.recipient(userId)
				.email("user@example.com")
				.template("PASSWORD_RESET")
				.category(Category.SYSTEM)
				.channel(Channel.BOTH)
				.build());

		assertThat(result).isPresent();
		verify(outboxRepo, never()).save(any());
	}
}
