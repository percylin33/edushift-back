package com.edushift.modules.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.entity.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthDtoTest {

    @Test
    @DisplayName("AuthResponse.bearer builds a Bearer-typed response")
    void bearerFactory() {
        var summary = new UserSummary(UUID.randomUUID(), "Ana Diaz", "ana@acme.test",
                "https://cdn.example.com/a.png", UserStatus.ACTIVE);
        var resp = AuthResponse.bearer("access.jwt", "refresh.jwt", 900L, summary);
        assertThat(resp.accessToken()).isEqualTo("access.jwt");
        assertThat(resp.refreshToken()).isEqualTo("refresh.jwt");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.expiresInSec()).isEqualTo(900L);
        assertThat(resp.user()).isEqualTo(summary);
    }

    @Test
    @DisplayName("AuthResponse full constructor lets callers override tokenType")
    void fullConstructor() {
        var summary = new UserSummary(UUID.randomUUID(), "Ana Diaz", "ana@acme.test",
                null, UserStatus.ACTIVE);
        var resp = new AuthResponse("a", "r", "MAC", 60L, summary);
        assertThat(resp.tokenType()).isEqualTo("MAC");
    }

    @Test
    @DisplayName("LoginRequest toString masks the password")
    void loginToStringMasksPassword() {
        var req = new LoginRequest("ana@acme.test", "Sup3rSecret!");
        assertThat(req.toString()).contains("ana@acme.test");
        assertThat(req.toString()).doesNotContain("Sup3rSecret!");
        assertThat(req.toString()).contains("password=***");
    }

    @Test
    @DisplayName("RefreshTokenRequest toString masks the token")
    void refreshToStringMasksToken() {
        var req = new RefreshTokenRequest("verylongopaque.jwt.value");
        assertThat(req.toString()).isEqualTo("RefreshTokenRequest[refreshToken=***]");
    }

    @Test
    @DisplayName("GoogleLoginRequest toString masks the idToken")
    void googleToStringMasksIdToken() {
        var req = new GoogleLoginRequest("ya29.a0AfH6SMB...long-jwt");
        assertThat(req.toString()).isEqualTo("GoogleLoginRequest[idToken=***]");
    }

    @Test
    @DisplayName("UserSummary exposes all accessors")
    void userSummaryAccessors() {
        UUID id = UUID.randomUUID();
        var s = new UserSummary(id, "Ana Diaz", "ana@acme.test", "https://x", UserStatus.ACTIVE);
        assertThat(s.publicUuid()).isEqualTo(id);
        assertThat(s.fullName()).isEqualTo("Ana Diaz");
        assertThat(s.email()).isEqualTo("ana@acme.test");
        assertThat(s.avatarUrl()).isEqualTo("https://x");
        assertThat(s.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("UserResponse exposes all accessors")
    void userResponseAccessors() {
        UUID id = UUID.randomUUID();
        var resp = new UserResponse(id, "Ana", "Diaz", "Ana Diaz", "ana@acme.test",
                "+51 999", null, UserStatus.ACTIVE, true, false, java.util.Set.of("TENANT_ADMIN"),
                java.util.Set.of("LMS_USERS_MANAGE"), null, null, null);
        assertThat(resp.publicUuid()).isEqualTo(id);
        assertThat(resp.fullName()).isEqualTo("Ana Diaz");
        assertThat(resp.roles()).containsExactly("TENANT_ADMIN");
        assertThat(resp.permissions()).containsExactly("LMS_USERS_MANAGE");
    }

    @Test
    @DisplayName("ChangePasswordRequest carries current + new password")
    void changePasswordRequest() {
        var req = new ChangePasswordRequest("oldPass1!", "NewStr0ng!");
        assertThat(req.currentPassword()).isEqualTo("oldPass1!");
        assertThat(req.newPassword()).isEqualTo("NewStr0ng!");
    }

    @Test
    @DisplayName("UpdateUserRequest stores profile-only fields")
    void updateUserRequest() {
        var req = new UpdateUserRequest("Maria", "Lopez", "+51 111", null);
        assertThat(req.firstName()).isEqualTo("Maria");
        assertThat(req.lastName()).isEqualTo("Lopez");
        assertThat(req.phone()).isEqualTo("+51 111");
        assertThat(req.avatarUrl()).isNull();
    }
}