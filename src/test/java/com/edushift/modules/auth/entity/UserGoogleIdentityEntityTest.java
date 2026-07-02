package com.edushift.modules.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserGoogleIdentityEntityTest {

    @Test
    @DisplayName("fresh identity is not revoked")
    void fresh() {
        var i = new UserGoogleIdentity();
        assertThat(i.isRevoked()).isFalse();
        assertThat(i.getRevokedAt()).isNull();
        assertThat(i.getRevokedReason()).isNull();
    }

    @Test
    @DisplayName("revoke stamps revokedAt + reason and is idempotent")
    void revokeIdempotent() {
        var i = new UserGoogleIdentity();
        i.revoke("user_disconnected");
        var firstRevokedAt = i.getRevokedAt();
        assertThat(i.isRevoked()).isTrue();
        assertThat(i.getRevokedReason()).isEqualTo("user_disconnected");

        // Second call should not overwrite the original timestamps
        try { Thread.sleep(5); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        i.revoke("admin_revoke");
        assertThat(i.getRevokedAt()).isEqualTo(firstRevokedAt);
        assertThat(i.getRevokedReason()).isEqualTo("user_disconnected");
    }

    @Test
    @DisplayName("encrypted refresh token + scopes are settable")
    void fieldsPersist() {
        var i = new UserGoogleIdentity();
        i.setEncryptedRefreshToken(new byte[] {0x01, 0x02, 0x03});
        i.setScopes(new String[] {"openid", "email", "profile"});
        i.setExpiresAt(Instant.now().plusSeconds(3600));
        assertThat(i.getEncryptedRefreshToken()).containsExactly(0x01, 0x02, 0x03);
        assertThat(i.getScopes()).containsExactly("openid", "email", "profile");
        assertThat(i.getExpiresAt()).isNotNull();
    }
}