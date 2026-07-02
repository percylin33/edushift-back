package com.edushift.modules.users.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvitationAcceptedEventTest {

    @Test
    @DisplayName("record accessor round-trip")
    void accessors() {
        UUID invitationId = UUID.randomUUID();
        UUID invitationPub = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID userPub = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var metadata = Map.<String, Object>of("teacherId", "019e");
        var when = Instant.parse("2026-01-01T00:00:00Z");

        var event = new InvitationAcceptedEvent(invitationId, invitationPub, userId,
                userPub, tenantId, metadata, when);

        assertThat(event.invitationId()).isEqualTo(invitationId);
        assertThat(event.invitationPublicUuid()).isEqualTo(invitationPub);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.userPublicUuid()).isEqualTo(userPub);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.metadata()).containsEntry("teacherId", "019e");
        assertThat(event.acceptedAt()).isEqualTo(when);
    }

    @Test
    @DisplayName("null metadata is permitted (used for invitations without side-channel data)")
    void nullMetadata() {
        var event = new InvitationAcceptedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, Instant.now());
        assertThat(event.metadata()).isNull();
    }
}