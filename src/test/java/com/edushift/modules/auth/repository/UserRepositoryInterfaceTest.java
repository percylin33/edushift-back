package com.edushift.modules.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class UserRepositoryInterfaceTest {

    @Test
    @DisplayName("findByPublicUuid returns Optional<User>")
    void findByPublicUuid() throws Exception {
        Method m = UserRepository.class.getMethod("findByPublicUuid", UUID.class);
        assertThat(m.getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("findByEmail + findByEmailAndTenantId")
    void findByEmail() throws Exception {
        assertThat(UserRepository.class.getMethod("findByEmail", String.class)
                .getReturnType()).isAssignableFrom(Optional.class);
        assertThat(UserRepository.class.getMethod("findByEmailAndTenantId", String.class, UUID.class)
                .getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("findByGoogleSubject + ...AndTenantId overload")
    void findByGoogleSubject() throws Exception {
        assertThat(UserRepository.class.getMethod("findByGoogleSubject", String.class)
                .getReturnType()).isAssignableFrom(Optional.class);
        assertThat(UserRepository.class.getMethod("findByGoogleSubjectAndTenantId",
                String.class, UUID.class).getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("existsByPublicUuid + existsByEmail + ...AndTenantId")
    void exists() throws Exception {
        assertThat(UserRepository.class.getMethod("existsByPublicUuid", UUID.class)
                .getReturnType()).isEqualTo(boolean.class);
        assertThat(UserRepository.class.getMethod("existsByEmail", String.class)
                .getReturnType()).isEqualTo(boolean.class);
        assertThat(UserRepository.class.getMethod("existsByEmailAndTenantId",
                String.class, UUID.class).getReturnType()).isEqualTo(boolean.class);
    }

    @Test
    @DisplayName("findAllByStatus returns Page<User>")
    void findAllByStatus() throws Exception {
        Method m = UserRepository.class.getMethod("findAllByStatus",
                UserStatus.class, Pageable.class);
        assertThat(m.getReturnType()).isAssignableFrom(Page.class);
    }

    @Test
    @DisplayName("countByStatus + countByTenantIdAndStatus return long")
    void count() throws Exception {
        assertThat(UserRepository.class.getMethod("countByStatus", UserStatus.class)
                .getReturnType()).isEqualTo(long.class);
        assertThat(UserRepository.class.getMethod("countByTenantIdAndStatus",
                UUID.class, UserStatus.class).getReturnType()).isEqualTo(long.class);
    }

    @Test
    @DisplayName("updateLastLoginAt and updateStatus return int (modifying queries)")
    void modifyingQueries() throws Exception {
        assertThat(UserRepository.class.getMethod("updateLastLoginAt",
                UUID.class, java.time.Instant.class).getReturnType()).isEqualTo(int.class);
        assertThat(UserRepository.class.getMethod("updateStatus",
                UUID.class, UserStatus.class).getReturnType()).isEqualTo(int.class);
    }

    @Test
    @DisplayName("countActiveTenantAdmins returns long (native query)")
    void countActiveTenantAdmins() throws Exception {
        Method m = UserRepository.class.getMethod("countActiveTenantAdmins", UUID.class);
        assertThat(m.getReturnType()).isEqualTo(long.class);
    }

    @Test
    @DisplayName("smoke: User entity can be constructed for the repository")
    void userSmoke() {
        var u = new User();
        u.setPublicUuid(UUID.randomUUID());
        u.setEmail("ana@acme.test");
        u.setStatus(UserStatus.ACTIVE);
        u.setRoles(new String[] {"TENANT_ADMIN"});
        assertThat(u.getRoleNames()).contains("TENANT_ADMIN");
        assertThat(List.of(u.getRoles())).hasSize(1);
    }
}