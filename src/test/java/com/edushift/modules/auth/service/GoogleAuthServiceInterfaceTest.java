package com.edushift.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.dto.AuthResponse;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke-level interface test for {@link GoogleAuthService}. The implementation
 * lives in {@code GoogleAuthServiceImpl}; this test only covers the contract
 * surface.
 */
class GoogleAuthServiceInterfaceTest {

    @Test
    @DisplayName("interface declares loginWithGoogle returning AuthResponse")
    void surface() throws Exception {
        Method m = GoogleAuthService.class.getMethod("loginWithGoogle",
                com.edushift.infrastructure.integrations.google.GoogleProfile.class,
                String.class, String.class);
        assertThat(m.getReturnType()).isEqualTo(AuthResponse.class);
    }

    @Test
    @DisplayName("interface is the right shape (single method)")
    void singleMethod() {
        var methods = GoogleAuthService.class.getDeclaredMethods();
        assertThat(methods).hasSize(1);
    }
}