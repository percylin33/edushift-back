package com.edushift.modules.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenRepositoryInterfaceTest {

    @Test
    @DisplayName("findByTokenHash returns Optional")
    void findByTokenHash() throws Exception {
        Method m = RefreshTokenRepository.class.getMethod("findByTokenHash", String.class);
        assertThat(m.getReturnType()).isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("findActiveByUserId returns List")
    void findActiveByUserId() throws Exception {
        Method m = RefreshTokenRepository.class.getMethod("findActiveByUserId",
                UUID.class, java.time.Instant.class);
        assertThat(m.getReturnType()).isAssignableFrom(java.util.List.class);
    }

    @Test
    @DisplayName("revokeChain has String + enum overloads, both return int")
    void revokeChain() throws Exception {
        Method stringOverload = RefreshTokenRepository.class.getMethod("revokeChain",
                UUID.class, String.class);
        assertThat(stringOverload.getReturnType()).isEqualTo(int.class);
        Method enumOverload = RefreshTokenRepository.class.getMethod("revokeChain",
                UUID.class, com.edushift.modules.auth.entity.RevocationReason.class);
        assertThat(enumOverload.getReturnType()).isEqualTo(int.class);
    }

    @Test
    @DisplayName("smoke: RevocationReason enum has 5 values")
    void enumSurface() {
        assertThat(com.edushift.modules.auth.entity.RevocationReason.values()).hasSize(5);
    }
}