package com.edushift.modules.notifications.entity;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class NotificationsEntityTest {
    @Test void notificationSetters() { var n = new Notification(); n.setPublicUuid(UUID.randomUUID()); n.setRecipientUserId(UUID.randomUUID()); n.setTemplateKey("welcome"); n.setChannel(Notification.Channel.IN_APP); n.setCategory(Notification.Category.SYSTEM); n.setStatus(Notification.Status.SENT); n.setPayload("{}"); assertThat(n.getTemplateKey()).isEqualTo("welcome"); assertThat(n.getChannel()).isEqualTo(Notification.Channel.IN_APP); }
    @Test void preferenceSetters() { var p = new NotificationPreference(); p.setUserId(UUID.randomUUID()); p.setChannel(Notification.Channel.EMAIL); p.setCategory(Notification.Category.GRADE); p.setEnabled(false); assertThat(p.getChannel()).isEqualTo(Notification.Channel.EMAIL); }
}
