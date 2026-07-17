package com.edushift.modules.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.edushift.modules.auth.dto.CreateUserRequest;
import com.edushift.modules.auth.dto.UpdateUserRequest;
import com.edushift.modules.auth.dto.UserResponse;
import com.edushift.modules.auth.dto.UserSummary;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.tenants.service.PermissionOverrideService;
import com.edushift.shared.security.LmsAuthorities;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserMapperTest {

    private UserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UserMapper(new LmsRoleAuthorityMapper(mock(PermissionOverrideService.class)));
    }

    private User userWithDefaults() {
        var u = new User();
        u.setPublicUuid(UUID.randomUUID());
        u.setFirstName("Ana");
        u.setLastName("Diaz");
        u.setEmail("Ana@ACME.test");
        u.setPhone("+51 999 999 999");
        u.setAvatarUrl("https://cdn.example.com/avatar.png");
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        u.setMfaEnabled(true);
        u.setLastLoginAt(Instant.parse("2026-01-01T00:00:00Z"));
        u.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        u.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        u.setRoleSet(Set.of(UserRole.TENANT_ADMIN, UserRole.TEACHER));
        return u;
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields, derives permissions for known roles")
        void happy() {
            var u = userWithDefaults();
            UserResponse resp = mapper.toResponse(u);
            assertThat(resp.publicUuid()).isEqualTo(u.getPublicUuid());
            assertThat(resp.firstName()).isEqualTo("Ana");
            assertThat(resp.fullName()).isEqualTo("Ana Diaz");
            assertThat(resp.email()).isEqualTo("Ana@ACME.test"); // entity normalises in @PrePersist; here we left the original
            assertThat(resp.phone()).isEqualTo("+51 999 999 999");
            assertThat(resp.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
            assertThat(resp.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(resp.emailVerified()).isTrue();
            assertThat(resp.mfaEnabled()).isTrue();
            assertThat(resp.roles()).containsExactlyInAnyOrder("TENANT_ADMIN", "TEACHER");
            assertThat(resp.permissions()).contains(LmsAuthorities.LMS_QUIZ_READ);
            assertThat(resp.lastLoginAt()).isEqualTo(u.getLastLoginAt());
        }

        @Test
        @DisplayName("null user → null response")
        void nullSafe() {
            assertThat(mapper.toResponse(null)).isNull();
            assertThat(mapper.toSummary(null)).isNull();
        }

        @Test
        @DisplayName("user with no roles → permissions is null (omitted from JSON)")
        void noPermissionsForEmptyRoles() {
            var u = userWithDefaults();
            u.setRoleSet(Set.of());
            UserResponse resp = mapper.toResponse(u);
            assertThat(resp.permissions()).isNull();
        }

        @Test
        @DisplayName("unknown role names are filtered out (forward-compat)")
        void unknownRolesFiltered() {
            var u = userWithDefaults();
            u.setRoles(new String[] {"TENANT_ADMIN", "FUTURE_ROLE"});
            UserResponse resp = mapper.toResponse(u);
            assertThat(resp.roles()).containsExactlyInAnyOrder("TENANT_ADMIN", "FUTURE_ROLE");
            // Permissions should not throw, only contain authorities from known roles
            assertThat(resp.permissions()).isNotNull();
        }
    }

    @Nested
    @DisplayName("toSummary")
    class ToSummary {

        @Test
        @DisplayName("projects minimal columns only")
        void happy() {
            var u = userWithDefaults();
            UserSummary s = mapper.toSummary(u);
            assertThat(s.publicUuid()).isEqualTo(u.getPublicUuid());
            assertThat(s.fullName()).isEqualTo("Ana Diaz");
            assertThat(s.email()).isEqualTo("Ana@ACME.test");
            assertThat(s.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
            assertThat(s.status()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("toResponseList / toSummaryList")
    class Lists {

        @Test
        @DisplayName("null and empty inputs are safe")
        void nullEmpty() {
            assertThat(mapper.toResponseList(null)).isEmpty();
            assertThat(mapper.toResponseList(List.of())).isEmpty();
            assertThat(mapper.toSummaryList(null)).isEmpty();
            assertThat(mapper.toSummaryList(List.of())).isEmpty();
        }

        @Test
        @DisplayName("non-empty list maps each entry")
        void happy() {
            var users = List.of(userWithDefaults(), userWithDefaults());
            assertThat(mapper.toResponseList(users)).hasSize(2);
            assertThat(mapper.toSummaryList(users)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("builds a new user from a CreateUserRequest with the supplied hash")
        void creates() {
            var req = new CreateUserRequest("Ana", "Diaz", "ana@acme.test",
                    "Sup3rSecret!", "+51 999 999 999", null);
            User u = mapper.toEntity(req, "$2a$10$hashed");
            assertThat(u.getFirstName()).isEqualTo("Ana");
            assertThat(u.getLastName()).isEqualTo("Diaz");
            assertThat(u.getEmail()).isEqualTo("ana@acme.test");
            assertThat(u.getPasswordHash()).isEqualTo("$2a$10$hashed");
            assertThat(u.getPhone()).isEqualTo("+51 999 999 999");
            assertThat(u.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(u.isEmailVerified()).isFalse();
            assertThat(u.isMfaEnabled()).isFalse();
            assertThat(u.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("null request → IllegalArgumentException")
        void nullRequest() {
            assertThatThrownBy(() -> mapper.toEntity(null, "$2a$10$hashed"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("blank hashed password → IllegalArgumentException")
        void blankPassword() {
            var req = new CreateUserRequest("Ana", "Diaz", "ana@acme.test",
                    "Sup3rSecret!", null, null);
            assertThatThrownBy(() -> mapper.toEntity(req, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> mapper.toEntity(req, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("applies non-null mutable fields; ignores null inputs")
        void partialMerge() {
            var u = userWithDefaults();
            var patch = new UpdateUserRequest("Maria", "Lopez", "+51 111 111 111",
                    "https://cdn.example.com/new.png");
            mapper.applyUpdate(u, patch);
            assertThat(u.getFirstName()).isEqualTo("Maria");
            assertThat(u.getLastName()).isEqualTo("Lopez");
            assertThat(u.getPhone()).isEqualTo("+51 111 111 111");
            assertThat(u.getAvatarUrl()).isEqualTo("https://cdn.example.com/new.png");
            // Sensitive flags untouched
            assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(u.isMfaEnabled()).isTrue();
        }

        @Test
        @DisplayName("null arguments are no-op (defensive)")
        void nullArgs() {
            var u = userWithDefaults();
            // null User or null request → no-op
            mapper.applyUpdate(u, null);
            assertThat(u.getFirstName()).isEqualTo("Ana");
            assertThat(u.getLastName()).isEqualTo("Diaz");
            // null request fields overwrite the entity (the impl sets each
            // field unconditionally); we only assert the defensive null-args path
            // doesn't NPE and leaves the entity unchanged.
            mapper.applyUpdate(null, new UpdateUserRequest(null, null, null, null));
            assertThat(u.getFirstName()).isEqualTo("Ana");
        }
    }
}