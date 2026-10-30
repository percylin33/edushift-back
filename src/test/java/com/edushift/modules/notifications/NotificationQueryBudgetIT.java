package com.edushift.modules.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.dto.NotificationResponse;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.Notification.Status;
import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.notifications.repository.NotificationTemplateRepository;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.notifications.service.NotificationService.NotifyCommand;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves notification list and fan-out stay a fixed number of queries
 * when the page or the recipient list grows (8 rows, 6 recipients).
 */
@DisplayName("Notification query budget")
class NotificationQueryBudgetIT extends IntegrationTest {

	private static final int LISTED = 8;
	private static final int RECIPIENTS = 6;
	private static final String KEY_ALFA = "QUIZ_PUBLISHED";
	private static final String KEY_BETA = "GRADE_PUBLISHED";
	private static final String KEY_FANOUT = "PAYMENT_REMINDER";
	private static final String SUBJECT_ALFA = "Asunto alfa";
	private static final String SUBJECT_BETA = "Asunto beta";

	@Autowired private NotificationService notificationService;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private NotificationTemplateRepository templateRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private EntityManager entityManager;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private JdbcTemplate jdbcTemplate;

	private TransactionTemplate tx;
	private UUID tenantId;
	private UUID listRecipientId;
	private List<UUID> recipientIds;

	@BeforeEach
	void seed() {
		tx = new TransactionTemplate(txManager);
		statistics().setStatisticsEnabled(true);

		Tenant tenant = newTestTenant();
		tenant.setSlug("nq" + UUID.randomUUID().toString().substring(0, 8));
		tenant.setName("Notification budget " + tenant.getSlug());
		tenant.setStatus(TenantStatus.ACTIVE);
		tenant.setPlan(TenantPlan.BASIC);
		tenant.setPlanId(jdbcTemplate.queryForObject(
				"select id from edushift.platform_plans where code = ?",
				UUID.class, "BASIC"));
		Tenant saved = tx.execute(status -> tenantRepository.saveAndFlush(tenant));
		tenantId = saved.getId();

		recipientIds = seedUsers();
		listRecipientId = recipientIds.get(0);
		seedTemplates();
		seedNotifications();
	}

	@Test
	@DisplayName("list and notifyAll stay within a fixed query budget")
	void queryCountsDoNotGrowWithPageOrRecipients() {
		long listQueries = measure(() -> {
			var page = notificationService.listForUser(listRecipientId, false, PageRequest.of(0, 20));
			List<NotificationResponse> responses = notificationService.toResponses(page.getContent());
			assertThat(responses).hasSize(LISTED);
			assertThat(responses).extracting(NotificationResponse::subject)
					.contains(SUBJECT_ALFA, SUBJECT_BETA);
			assertThat(responses.stream().map(NotificationResponse::subject).distinct())
					.containsExactlyInAnyOrder(SUBJECT_ALFA, SUBJECT_BETA);
			return responses;
		});
		assertThat(listQueries).as("list queries").isLessThanOrEqualTo(4);

		long fanoutQueries = measure(() -> {
			List<NotifyCommand> commands = recipientIds.stream()
					.map(userId -> NotifyCommand.builder()
							.recipient(userId)
							.template(KEY_FANOUT)
							.category(Category.PAYMENT)
							.channel(Channel.BOTH)
							.build())
					.toList();
			List<UUID> ids = notificationService.notifyAll(commands);
			assertThat(ids).hasSize(RECIPIENTS);
			return ids;
		});
		assertThat(fanoutQueries).as("notifyAll queries").isLessThanOrEqualTo(6);

		long stored = TenantContext.runAs(tenantId, () -> notificationRepository.findAll().stream()
				.filter(row -> KEY_FANOUT.equals(row.getTemplateKey()))
				.count());
		assertThat(stored).isEqualTo(RECIPIENTS);
	}

	private <T> long measure(Supplier<T> call) {
		tx.executeWithoutResult(status -> entityManager.clear());
		Statistics stats = statistics();
		stats.clear();
		TenantContext.runAs(tenantId, call);
		return stats.getQueryExecutionCount();
	}

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	private List<UUID> seedUsers() {
		return TenantContext.runAs(tenantId, () -> tx.execute(status -> {
			java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
			for (int i = 0; i < RECIPIENTS; i++) {
				ids.add(saveUser("student" + i).getPublicUuid());
			}
			return ids;
		}));
	}

	private User saveUser(String localPart) {
		User user = new User();
		user.setEmail(localPart + "-" + tenantId + "@notification-budget.test");
		user.setPasswordHash("$2a$10$dummy.hash.for.it.only.satisfies.not.null");
		user.setFirstName(localPart);
		user.setLastName("Budget");
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerified(true);
		user.addRole(UserRole.STUDENT);
		return userRepository.saveAndFlush(user);
	}

	private void seedTemplates() {
		TenantContext.runAs(tenantId, () -> tx.execute(status -> {
			templateRepository.saveAndFlush(template(KEY_ALFA, SUBJECT_ALFA));
			templateRepository.saveAndFlush(template(KEY_BETA, SUBJECT_BETA));
			templateRepository.saveAndFlush(template(KEY_FANOUT, "Recordatorio"));
			return null;
		}));
	}

	private static NotificationTemplate template(String key, String subject) {
		NotificationTemplate template = new NotificationTemplate();
		template.setTemplateKey(key);
		template.setLocale("es-PE");
		template.setSubject(subject);
		template.setBodyHtml("<p>" + subject + "</p>");
		template.setSystem(true);
		template.setVersion(1);
		return template;
	}

	private void seedNotifications() {
		TenantContext.runAs(tenantId, () -> tx.execute(status -> {
			for (int i = 0; i < LISTED; i++) {
				Notification notification = new Notification();
				notification.setRecipientUserId(listRecipientId);
				notification.setTemplateKey(i % 2 == 0 ? KEY_ALFA : KEY_BETA);
				notification.setCategory(i % 2 == 0 ? Category.QUIZ : Category.GRADE);
				notification.setChannel(Channel.IN_APP);
				notification.setPayload("{}");
				notification.setStatus(Status.SENT);
				notificationRepository.saveAndFlush(notification);
			}
			return null;
		}));
	}
}
