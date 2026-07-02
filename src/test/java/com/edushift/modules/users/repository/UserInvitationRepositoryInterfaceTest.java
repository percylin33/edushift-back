package com.edushift.modules.users.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.users.entity.UserInvitation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lightweight repository contract test. The real persistence-layer test for
 * the JPA custom queries is the {@code UserInvitationTenantIsolationIT}
 * integration test (Testcontainers). This unit test only checks that the
 * query methods exist with the expected signatures.
 */
class UserInvitationRepositoryInterfaceTest {

    private final UserInvitationRepository repo = null; // not instantiated

    @Test
    @DisplayName("findByPublicUuid is declared")
    void findByPublicUuid() throws Exception {
        Method m = UserInvitationRepository.class.getMethod("findByPublicUuid", UUID.class);
        assertThat(m.getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("findActiveByToken is declared (native query)")
    void findActiveByToken() throws Exception {
        Method m = UserInvitationRepository.class.getMethod("findActiveByToken", String.class);
        assertThat(m.getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("findPendingInTenant returns Page")
    void findPendingInTenant() throws Exception {
        Method m = UserInvitationRepository.class.getMethod("findPendingInTenant",
                Instant.class, org.springframework.data.domain.Pageable.class);
        assertThat(m.getReturnType()).isAssignableFrom(org.springframework.data.domain.Page.class);
    }

    @Test
    @DisplayName("findActivePendingByEmail returns Optional")
    void findActivePendingByEmail() throws Exception {
        Method m = UserInvitationRepository.class.getMethod("findActivePendingByEmail",
                String.class, Instant.class);
        assertThat(m.getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("smoke: UserInvitation entity can be constructed")
    void entityShape() {
        var inv = new UserInvitation();
        inv.setPublicUuid(UUID.randomUUID());
        inv.setEmail("ana@acme.test");
        inv.setFirstName("Ana");
        inv.setLastName("Diaz");
        inv.setExpiresAt(Instant.now().plusSeconds(3600));
        // @PrePersist runs at flush time; this assertion is just to
        // verify the entity is wireable.
        assertThat(inv.getEmail()).isEqualTo("ana@acme.test");
        // list helper
        assertThat(List.of()).isEmpty();
    }
}