package com.edushift.modules.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.dto.NotificationResponse;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.Notification.Status;
import com.edushift.modules.notifications.entity.NotificationPreference;
import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.edushift.modules.notifications.repository.EmailOutboxRepository;
import com.edushift.modules.notifications.repository.NotificationPreferenceRepository;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.notifications.repository.NotificationTemplateRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
	@Mock UserRepository userRepository;
	@Mock RecipientEmailResolver recipientEmailResolver;
	@InjectMocks NotificationService service;

	@BeforeEach
	void setUp() {
		TenantContext.set(UUID.randomUUID());
		ReflectionTestUtils.setField(service, "emailEnabled", false);
		ReflectionTestUtils.setField(service, "recipientEmailResolver",
				new RecipientEmailResolver(userRepository));
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
	void notifySkipsOutboxWhenEmailDisabled() {
		UUID userId = UUID.randomUUID();
		stubTemplate("PASSWORD_RESET", "Restablece tu contraseña");
		when(preferenceRepo.findAllByUserIdInAndCategory(any(), any())).thenReturn(List.of());
		when(engine.render(any(), any())).thenReturn(
				new NotificationTemplateEngine.Rendered("Restablece tu contraseña", "<p>hi</p>"));
		when(notificationRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

		Optional<UUID> result = service.notify(NotificationService.NotifyCommand.builder()
				.recipient(userId)
				.email("user@example.com")
				.template("PASSWORD_RESET")
				.category(Category.SYSTEM)
				.channel(Channel.BOTH)
				.build());

		assertThat(result).isPresent();
		verify(outboxRepo, never()).save(any());
		verify(outboxRepo, never()).saveAll(any());
	}

	@Test
	void notifySkipsRowWhenBothChannelsAreOptedOut() {
		UUID userId = UUID.randomUUID();
		stubTemplate("PASSWORD_RESET", "Restablece tu contraseña");
		when(preferenceRepo.findAllByUserIdInAndCategory(any(), any()))
				.thenReturn(List.of(preference(userId, Channel.IN_APP), preference(userId, Channel.EMAIL)));

		Optional<UUID> result = service.notify(NotificationService.NotifyCommand.builder()
				.recipient(userId)
				.email("user@example.com")
				.template("PASSWORD_RESET")
				.category(Category.SYSTEM)
				.channel(Channel.BOTH)
				.build());

		assertThat(result).isEmpty();
		verify(notificationRepo, never()).saveAll(any());
		verify(preferenceRepo, never()).findByUserIdAndChannelAndCategory(any(), any(), any());
	}

	@Test
	void listLoadsTemplatesOnce() {
		NotificationTemplate quiz = persisted("QUIZ_PUBLISHED", "Asunto alfa");
		NotificationTemplate grade = persisted("GRADE_PUBLISHED", "Asunto beta");
		when(templateRepo.findAllByTemplateKeyInAndLocale(any(), eq("es-PE")))
				.thenReturn(List.of(quiz, grade));
		when(engine.render(any(), any())).thenAnswer(inv -> {
			NotificationTemplate template = inv.getArgument(0);
			return new NotificationTemplateEngine.Rendered(template.getSubject(), template.getBodyHtml());
		});

		List<NotificationResponse> responses = service.toResponses(List.of(
				notification("QUIZ_PUBLISHED"),
				notification("GRADE_PUBLISHED"),
				notification("QUIZ_PUBLISHED")));

		assertThat(responses).extracting(NotificationResponse::subject)
				.containsExactly("Asunto alfa", "Asunto beta", "Asunto alfa");
		verify(templateRepo, times(1)).findAllByTemplateKeyInAndLocale(any(), eq("es-PE"));
		verify(templateRepo, never()).findByKeyAndLocale(any(), any());
	}

	@Test
	void notifyAllUsesOneBatchPerDependency() {
		stubTemplate("QUIZ_PUBLISHED", "Asunto alfa");
		when(preferenceRepo.findAllByUserIdInAndCategory(any(), any())).thenReturn(List.of());
		when(engine.render(any(), any())).thenReturn(
				new NotificationTemplateEngine.Rendered("Asunto alfa", "<p>hola</p>"));
		List<User> users = new ArrayList<>();
		List<NotificationService.NotifyCommand> commands = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			UUID userId = UUID.randomUUID();
			User user = new User();
			user.setPublicUuid(userId);
			user.setEmail("student" + i + "@school.test");
			users.add(user);
			commands.add(NotificationService.NotifyCommand.builder()
					.recipient(userId)
					.template("QUIZ_PUBLISHED")
					.category(Category.QUIZ)
					.channel(Channel.BOTH)
					.build());
		}
		when(userRepository.findAllByPublicUuidIn(any())).thenReturn(users);
		when(notificationRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

		List<UUID> ids = service.notifyAll(commands);

		assertThat(ids).hasSize(3);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Iterable<Notification>> saved = ArgumentCaptor.forClass(Iterable.class);
		verify(notificationRepo, times(1)).saveAll(saved.capture());
		assertThat(saved.getValue())
				.hasSize(3)
				.allSatisfy(row -> {
					assertThat(row.getChannel()).isEqualTo(Channel.BOTH);
					assertThat(row.getStatus()).isEqualTo(Status.SENT);
				});
		verify(templateRepo, times(1)).findAllByTemplateKeyInAndLocale(any(), eq("es-PE"));
		verify(templateRepo, never()).findByKeyAndLocale(any(), any());
		verify(preferenceRepo, times(1)).findAllByUserIdInAndCategory(any(), eq(Category.QUIZ));
		verify(preferenceRepo, never()).findByUserIdAndChannelAndCategory(any(), any(), any());
		verify(userRepository, times(1)).findAllByPublicUuidIn(any());
		verify(userRepository, never()).findByPublicUuid(any());
		verify(outboxRepo, never()).saveAll(any());
	}

	private void stubTemplate(String key, String subject) {
		when(templateRepo.findAllByTemplateKeyInAndLocale(any(), eq("es-PE")))
				.thenReturn(List.of(persisted(key, subject)));
	}

	private static NotificationTemplate persisted(String key, String subject) {
		NotificationTemplate template = new NotificationTemplate();
		ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
		template.setTemplateKey(key);
		template.setLocale("es-PE");
		template.setSubject(subject);
		template.setBodyHtml("<p>hola</p>");
		return template;
	}

	private static Notification notification(String templateKey) {
		Notification notification = new Notification();
		notification.setPublicUuid(UUID.randomUUID());
		notification.setTemplateKey(templateKey);
		notification.setCategory(Category.QUIZ);
		notification.setChannel(Channel.IN_APP);
		notification.setStatus(Status.SENT);
		notification.setPayload("{}");
		return notification;
	}

	private static NotificationPreference preference(UUID userId, Channel channel) {
		NotificationPreference preference = new NotificationPreference();
		preference.setUserId(userId);
		preference.setChannel(channel);
		preference.setCategory(Category.SYSTEM);
		preference.setEnabled(false);
		return preference;
	}
}
