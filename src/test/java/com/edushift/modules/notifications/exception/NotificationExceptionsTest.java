package com.edushift.modules.notifications.exception;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class NotificationExceptionsTest {
    @Test void templateNotFound() { assertThat(new NotificationTemplateNotFoundException()).hasMessageContaining("template"); }
}
