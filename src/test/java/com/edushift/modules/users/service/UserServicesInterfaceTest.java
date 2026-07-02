package com.edushift.modules.users.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.users.dto.AcceptInvitationRequest;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationPreflightResponse;
import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.dto.AssignRolesRequest;
import com.edushift.modules.users.dto.UpdateUserRequest;
import com.edushift.modules.users.dto.UserDetailResponse;
import com.edushift.modules.users.dto.UserListFilters;
import com.edushift.modules.users.dto.UserListItem;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Smoke-level interface tests for the {@code users} module's two public
 * services. Verifies method shapes that controllers / IT scripts rely on.
 */
class UserServicesInterfaceTest {

    @Test
    @DisplayName("UserInvitationService surface")
    void invitationService() throws Exception {
        var iface = UserInvitationService.class;

        assertThat(iface.getMethod("createInvitation", CreateInvitationRequest.class)
                .getReturnType()).isEqualTo(InvitationResponse.class);
        assertThat(iface.getMethod("listPendingInvitations",
                org.springframework.data.domain.Pageable.class)
                .getReturnType()).isAssignableFrom(Page.class);

        var cancel = iface.getMethod("cancelInvitation", UUID.class);
        assertThat(cancel.getReturnType()).isEqualTo(InvitationResponse.class);

        assertThat(iface.getMethod("getPreflight", String.class)
                .getReturnType()).isEqualTo(InvitationPreflightResponse.class);
        assertThat(iface.getMethod("acceptInvitation", AcceptInvitationRequest.class)
                .getReturnType()).isEqualTo(AuthResponse.class);
    }

    @Test
    @DisplayName("UserManagementService surface")
    void managementService() throws Exception {
        var iface = UserManagementService.class;

        var list = iface.getMethod("listUsers", UserListFilters.class,
                org.springframework.data.domain.Pageable.class);
        assertThat(list.getReturnType()).isAssignableFrom(Page.class);

        var get = iface.getMethod("getUser", UUID.class);
        assertThat(get.getReturnType()).isEqualTo(UserDetailResponse.class);

        var update = iface.getMethod("updateUser", UUID.class, UpdateUserRequest.class);
        assertThat(update.getReturnType()).isEqualTo(UserDetailResponse.class);

        var assign = iface.getMethod("assignRoles", UUID.class, AssignRolesRequest.class);
        assertThat(assign.getReturnType()).isEqualTo(UserDetailResponse.class);

        assertThat(iface.getMethod("disableUser", UUID.class)
                .getReturnType()).isEqualTo(UserDetailResponse.class);
        assertThat(iface.getMethod("enableUser", UUID.class)
                .getReturnType()).isEqualTo(UserDetailResponse.class);
        assertThat(iface.getMethod("resetPassword", UUID.class)
                .getReturnType()).isEqualTo(void.class);
    }

    @Test
    @DisplayName("smoke: UserListFilters round-trip")
    void filters() {
        var f = new UserListFilters("ana",
                com.edushift.modules.auth.entity.UserStatus.ACTIVE, "TEACHER");
        Page<UserListItem> page = new PageImpl<>(java.util.List.of(), PageRequest.of(0, 20), 0);
        assertThat(f.hasAnyFilter()).isTrue();
        assertThat(page).isEmpty();
    }

    @Test
    @DisplayName("smoke: AssignRolesRequest with a non-empty set is constructed")
    void assignRoles() {
        var req = new AssignRolesRequest(Set.of("TEACHER"));
        assertThat(req.roles()).containsExactly("TEACHER");
    }
}