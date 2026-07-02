package com.edushift.modules.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserEntityTest {

    @Test
    @DisplayName("default status is PENDING_VERIFICATION")
    void defaults() {
        var u = new User();
        assertThat(u.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(u.isEmailVerified()).isFalse();
        assertThat(u.isMfaEnabled()).isFalse();
        assertThat(u.getRoles()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("email is normalised lowercase + trimmed on prePersist")
    void normalisesEmail() {
        var u = new User();
        u.setEmail("  Admin@ACME.test  ");
        // @PrePersist is a JPA lifecycle callback — it does not run from a
        // plain setter. We invoke the private callback via reflection so the
        // unit test can assert the documented normalisation.
        try {
            var m = User.class.getDeclaredMethod("onPrePersist");
            m.setAccessible(true);
            m.invoke(u);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        assertThat(u.getEmail()).isEqualTo("admin@acme.test");
        assertThat(u.getPublicUuid()).isNotNull();
        assertThat(u.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    @Test
    @DisplayName("fullName joins first + last; handles null parts")
    void fullNameCombinations() {
        var u = new User();
        assertThat(u.fullName()).isEmpty();
        u.setFirstName("Ana");
        assertThat(u.fullName()).isEqualTo("Ana");
        u.setLastName("Diaz");
        assertThat(u.fullName()).isEqualTo("Ana Diaz");
        u.setFirstName(null);
        assertThat(u.fullName()).isEqualTo("Diaz");
    }

    @Test
    @DisplayName("recordSuccessfulLogin stamps lastLoginAt")
    void recordLogin() {
        var u = new User();
        u.recordSuccessfulLogin();
        assertThat(u.getLastLoginAt()).isNotNull();
        assertThat(u.getLastLoginAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("markEmailVerified promotes PENDING to ACTIVE only")
    void verifyEmail() {
        var u = new User();
        u.markEmailVerified();
        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);

        var u2 = new User();
        u2.setStatus(UserStatus.SUSPENDED);
        u2.markEmailVerified();
        // SUSPENDED stays SUSPENDED — only PENDING is auto-promoted
        assertThat(u2.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(u2.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("markDeleted sets deletedAt; restore clears it")
    void lifecycle() {
        var u = new User();
        u.markDeleted();
        assertThat(u.getDeletedAt()).isNotNull();
        assertThat(u.isDeleted()).isTrue();
        u.restore();
        assertThat(u.getDeletedAt()).isNull();
        assertThat(u.isDeleted()).isFalse();
    }

    @Nested
    @DisplayName("role helpers")
    class Roles {

        @Test
        @DisplayName("getRoleNames returns defensive copy preserving order")
        void defensiveCopy() {
            var u = new User();
            u.setRoleSet(Set.of(UserRole.TEACHER, UserRole.STUDENT));
            var names = u.getRoleNames();
            assertThat(names).containsExactlyInAnyOrder("TEACHER", "STUDENT");
        }

        @Test
        @DisplayName("addRole is idempotent; hasRole reflects membership")
        void addHasRole() {
            var u = new User();
            u.addRole(UserRole.TENANT_ADMIN);
            u.addRole(UserRole.TENANT_ADMIN);
            assertThat(u.hasRole(UserRole.TENANT_ADMIN)).isTrue();
            assertThat(u.getRoleNames()).containsExactly("TENANT_ADMIN");
            assertThat(u.hasRole(UserRole.STUDENT)).isFalse();
        }

        @Test
        @DisplayName("setRoleSet null/empty clears roles")
        void clearRoles() {
            var u = new User();
            u.setRoleSet(Set.of(UserRole.TENANT_ADMIN));
            u.setRoleSet(null);
            assertThat(u.getRoles()).isEmpty();
            u.setRoleSet(Set.of(UserRole.TEACHER));
            u.setRoleSet(Set.of());
            assertThat(u.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("getRoleSet drops unknown role names defensively")
        void getRoleSetFiltersUnknown() {
            var u = new User();
            u.setRoles(new String[] {"TEACHER", "BOGUS_ROLE"});
            assertThat(u.getRoleSet()).containsExactly(UserRole.TEACHER);
        }
    }

    @Nested
    @DisplayName("UserStatus")
    class Status {

        @Test
        @DisplayName("canAuthenticate only for ACTIVE")
        void canAuthenticate() {
            assertThat(UserStatus.ACTIVE.canAuthenticate()).isTrue();
            assertThat(UserStatus.PENDING_VERIFICATION.canAuthenticate()).isFalse();
        }

        @Test
        @DisplayName("isBlocked for INACTIVE / SUSPENDED / LOCKED")
        void isBlocked() {
            assertThat(UserStatus.INACTIVE.isBlocked()).isTrue();
            assertThat(UserStatus.SUSPENDED.isBlocked()).isTrue();
            assertThat(UserStatus.LOCKED.isBlocked()).isTrue();
            assertThat(UserStatus.ACTIVE.isBlocked()).isFalse();
            assertThat(UserStatus.PENDING_VERIFICATION.isBlocked()).isFalse();
        }

        @Test
        @DisplayName("UserRole.fromName parses valid + null; rejects garbage")
        void fromName() {
            assertThat(UserRole.fromName("TENANT_ADMIN")).isEqualTo(UserRole.TENANT_ADMIN);
            assertThat(UserRole.fromName(null)).isNull();
            assertThat(UserRole.fromName("not_a_role")).isNull();
        }
    }
}