package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.ai.exception.AiRateLimitedException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RateLimitServiceTest {

    private RateLimitService service() {
        var s = new RateLimitService();
        ReflectionTestUtils.setField(s, "perHour", 2);
        ReflectionTestUtils.setField(s, "perDay", 5);
        return s;
    }

    @Test
    @DisplayName("checkAndIncrement increments under cap and allows the call")
    void underCap() {
        var s = service();
        UUID user = UUID.randomUUID();
        s.checkAndIncrement(user);
        s.checkAndIncrement(user);
        assertThat(s.getPerHour()).isEqualTo(2);
        assertThat(s.getPerDay()).isEqualTo(5);
    }

    @Test
    @DisplayName("per-hour cap throws AiRateLimitedException after perHour requests")
    void hourlyCap() {
        var s = service();
        UUID user = UUID.randomUUID();
        s.checkAndIncrement(user);
        s.checkAndIncrement(user);
        assertThatThrownBy(() -> s.checkAndIncrement(user))
                .isInstanceOf(AiRateLimitedException.class)
                .hasMessageContaining("limite de 2 generaciones de IA por hora");
    }

    @Test
    @DisplayName("per-user buckets are independent")
    void independentBuckets() {
        var s = service();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        s.checkAndIncrement(u1);
        s.checkAndIncrement(u1);
        // u1 is at the hourly cap, but u2 is still untouched
        assertThatThrownBy(() -> s.checkAndIncrement(u1))
                .isInstanceOf(AiRateLimitedException.class);
        s.checkAndIncrement(u2);
        s.checkAndIncrement(u2);
    }

    @Test
    @DisplayName("hourly counter resets when the hour window rolls over")
    void hourlyReset() throws Exception {
        var s = service();
        UUID user = UUID.randomUUID();
        // Saturate
        s.checkAndIncrement(user);
        s.checkAndIncrement(user);
        assertThatThrownBy(() -> s.checkAndIncrement(user))
                .isInstanceOf(AiRateLimitedException.class);

        // Force the bucket to look like its window has rolled over.
        // ReflectionTestUtils#getField unwraps nested fields; the buckets
        // map is private, so we walk to it via the public accessor pattern
        // (peek inside the Bucket by reflecting on its volatile fields).
        var bucketsField = RateLimitService.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var buckets = (java.util.Map<UUID, Object>) bucketsField.get(s);
        Object bucket = buckets.get(user);

        var hourReset = bucket.getClass().getDeclaredField("hourReset");
        hourReset.setAccessible(true);
        hourReset.set(bucket, java.time.Instant.now().minusSeconds(10));
        var dayReset = bucket.getClass().getDeclaredField("dayReset");
        dayReset.setAccessible(true);
        dayReset.set(bucket, java.time.Instant.now().plusSeconds(86_400));

        // Now the hourly counter should be reset and the call goes through
        s.checkAndIncrement(user);
    }
}