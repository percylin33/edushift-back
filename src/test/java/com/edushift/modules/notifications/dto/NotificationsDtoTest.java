package com.edushift.modules.notifications.dto;
import static org.assertj.core.api.Assertions.assertThat;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.Notification.Status;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class NotificationsDtoTest {
    @Test void notificationResponse() { var r = new NotificationResponse(UUID.randomUUID(), "T", Category.SYSTEM, Channel.IN_APP, Status.SENT, "Sub", "<p>Body</p>", Instant.now(), null); assertThat(r.subject()).isEqualTo("Sub"); }
    @Test void unreadCountResponse() { var r = new UnreadCountResponse(3L); assertThat(r.unreadCount()).isEqualTo(3L); }
}
