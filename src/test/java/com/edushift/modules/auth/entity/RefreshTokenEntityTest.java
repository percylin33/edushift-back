package com.edushift.modules.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenEntityTest {

    @Test
    @DisplayName("fresh token is active: not revoked, not expired")
    void fresh() {
        var t = new RefreshToken();
        t.setExpiresAt(Instant.now().plusSeconds(3600));
        assertThat(t.isRevoked()).isFalse();
        assertThat(t.isExpired()).isFalse();
        assertThat(t.isActive()).isTrue();
    }

    @Test
    @DisplayName("revoke stamps revokedAt and is idempotent")
    void revokeIdempotent() {
        var t = new RefreshToken();
        t.setExpiresAt(Instant.now().plusSeconds(3600));
        t.revoke(RevocationReason.LOGOUT);
        var firstRevokedAt = t.getRevokedAt();
        assertThat(t.isRevoked()).isTrue();
        assertThat(t.getRevokedReason()).isEqualTo(RevocationReason.LOGOUT);

        // Calling again should not change the original revokedAt
        try { Thread.sleep(5); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        t.revoke(RevocationReason.ADMIN_REVOKE);
        assertThat(t.getRevokedAt()).isEqualTo(firstRevokedAt);
        // First reason wins
        assertThat(t.getRevokedReason()).isEqualTo(RevocationReason.LOGOUT);
    }

    @Test
    @DisplayName("expired when expiresAt is in the past")
    void expiredDetection() {
        var t = new RefreshToken();
        t.setExpiresAt(Instant.now().minusSeconds(10));
        assertThat(t.isExpired()).isTrue();
        assertThat(t.isActive()).isFalse();
    }

    @Test
    @DisplayName("revoked token is inactive even if not expired")
    void revokedInactive() {
        var t = new RefreshToken();
        t.setExpiresAt(Instant.now().plusSeconds(3600));
        t.revoke(RevocationReason.COMPROMISED);
        assertThat(t.isActive()).isFalse();
    }

    @Test
    @DisplayName("markDeleted stamps deletedAt; restore clears it")
    void lifecycle() {
        var t = new RefreshToken();
        t.setExpiresAt(Instant.now().plusSeconds(60));
        t.markDeleted();
        assertThat(t.getDeletedAt()).isNotNull();
        assertThat(t.isDeleted()).isTrue();
        t.restore();
        assertThat(t.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("RevocationReason has 5 expected values")
    void enumSurface() {
        assertThat(RevocationReason.values()).hasSize(5);
        assertThat(RevocationReason.valueOf("ROTATED")).isEqualTo(RevocationReason.ROTATED);
    }
}