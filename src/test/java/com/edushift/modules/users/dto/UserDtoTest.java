package com.edushift.modules.users.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.users.entity.InvitationStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserDtoTest {

    @Test
    @DisplayName("UserListItem exposes record accessors")
    void userListItem() {
        UUID id = UUID.randomUUID();
        var item = new UserListItem(id, "ana@acme.test", "Ana", "Diaz", "Ana Diaz",
                com.edushift.modules.auth.entity.UserStatus.ACTIVE,
                Set.of("TENANT_ADMIN"), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(item.publicUuid()).isEqualTo(id);
        assertThat(item.email()).isEqualTo("ana@acme.test");
        assertThat(item.fullName()).isEqualTo("Ana Diaz");
        assertThat(item.status()).isEqualTo(com.edushift.modules.auth.entity.UserStatus.ACTIVE);
        assertThat(item.roles()).containsExactly("TENANT_ADMIN");
    }

    @Test
    @DisplayName("UserDetailResponse exposes record accessors")
    void userDetailResponse() {
        UUID id = UUID.randomUUID();
        var r = new UserDetailResponse(id, "ana@acme.test", "Ana", "Diaz", "Ana Diaz",
                "+51 999", null, com.edushift.modules.auth.entity.UserStatus.ACTIVE, true, false,
                Set.of("TEACHER"), null, null, null);
        assertThat(r.publicUuid()).isEqualTo(id);
        assertThat(r.phone()).isEqualTo("+51 999");
        assertThat(r.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("UpdateUserRequest.isEmpty when every field is null")
    void updateUserIsEmpty() {
        assertThat(new UpdateUserRequest(null, null, null, null).isEmpty()).isTrue();
        assertThat(new UpdateUserRequest("Ana", null, null, null).isEmpty()).isFalse();
        assertThat(new UpdateUserRequest(null, null, null, "url").isEmpty()).isFalse();
    }

    @Test
    @DisplayName("UserListFilters.empty + hasAnyFilter")
    void userListFilters() {
        var empty = UserListFilters.empty();
        assertThat(empty.hasAnyFilter()).isFalse();
        assertThat(new UserListFilters("ana", null, null).hasAnyFilter()).isTrue();
        assertThat(new UserListFilters(null,
                com.edushift.modules.auth.entity.UserStatus.ACTIVE, null).hasAnyFilter()).isTrue();
        assertThat(new UserListFilters(" ", null, " ").hasAnyFilter()).isFalse();
    }

    @Test
    @DisplayName("AssignRolesRequest requires non-null + non-empty roles")
    void assignRolesRequest() {
        var req = new AssignRolesRequest(Set.of("TEACHER"));
        assertThat(req.roles()).containsExactly("TEACHER");
    }

    @Test
    @DisplayName("CreateInvitationRequest has a 4-arg convenience constructor")
    void createInvitationConvenience() {
        var req = new CreateInvitationRequest("ana@acme.test", "Ana", "Diaz",
                Set.of("TEACHER"));
        assertThat(req.email()).isEqualTo("ana@acme.test");
        assertThat(req.metadata()).isNull();
    }

    @Test
    @DisplayName("CreateInvitationRequest with full constructor stores metadata")
    void createInvitationFull() {
        var req = new CreateInvitationRequest("ana@acme.test", "Ana", "Diaz",
                Set.of("TEACHER"), java.util.Map.of("teacherId", "019e"));
        assertThat(req.metadata()).containsEntry("teacherId", "019e");
    }

    @Test
    @DisplayName("AcceptInvitationRequest stores token + password")
    void acceptInvitationRequest() {
        var req = new AcceptInvitationRequest("verylongtokenvalue", "Str0ng!Pass1");
        assertThat(req.token()).isEqualTo("verylongtokenvalue");
        assertThat(req.password()).isEqualTo("Str0ng!Pass1");
    }

    @Test
    @DisplayName("InvitationPreflightResponse stores public fields only")
    void preflight() {
        var p = new InvitationPreflightResponse("ana@acme.test", "Ana", "Diaz", "Acme School");
        assertThat(p.email()).isEqualTo("ana@acme.test");
        assertThat(p.tenantName()).isEqualTo("Acme School");
    }

    @Test
    @DisplayName("InvitationResponse.withoutToken drops the token")
    void invitationWithoutToken() {
        UUID id = UUID.randomUUID();
        var full = new InvitationResponse(id, "ana@acme.test", "Ana", "Diaz",
                Set.of("TEACHER"), InvitationStatus.PENDING, "secret.token.value",
                Instant.now(), null, null, Instant.now());
        var stripped = full.withoutToken();
        assertThat(stripped.publicUuid()).isEqualTo(id);
        assertThat(stripped.token()).isNull();
        assertThat(stripped.email()).isEqualTo("ana@acme.test");
        assertThat(stripped.status()).isEqualTo(InvitationStatus.PENDING);
    }
}