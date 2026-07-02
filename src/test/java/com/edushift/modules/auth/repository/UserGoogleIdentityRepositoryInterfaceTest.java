package com.edushift.modules.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserGoogleIdentityRepositoryInterfaceTest {

    @Test
    @DisplayName("findByUserIdAndRevokedAtIsNull returns Optional")
    void findActive() throws Exception {
        Method m = UserGoogleIdentityRepository.class.getMethod("findByUserIdAndRevokedAtIsNull",
                UUID.class);
        assertThat(m.getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("findByUserIdAndTenantIdAndRevokedAtIsNull returns Optional")
    void findActiveSystemOverload() throws Exception {
        Method m = UserGoogleIdentityRepository.class.getMethod(
                "findByUserIdAndTenantIdAndRevokedAtIsNull", UUID.class, UUID.class);
        assertThat(m.getReturnType()).isAssignableFrom(Optional.class);
    }
}