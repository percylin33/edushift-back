package com.edushift.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CurrentUserProviderTest {

    @Test
    @DisplayName("CurrentUserProvider is an interface")
    void isInterface() {
        assertThat(CurrentUserProvider.class.isInterface()).isTrue();
    }

    @Test
    @DisplayName("declares currentUserId returning Optional<UUID>")
    void declaresCurrentUserId() throws Exception {
        Method method = CurrentUserProvider.class.getDeclaredMethod("currentUserId");
        assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
        assertThat(method.getReturnType()).isEqualTo(Optional.class);
    }

    @Test
    @DisplayName("declares currentUsername returning Optional<String>")
    void declaresCurrentUsername() throws Exception {
        Method method = CurrentUserProvider.class.getDeclaredMethod("currentUsername");
        assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
        assertThat(method.getReturnType()).isEqualTo(Optional.class);
    }

    @Test
    @DisplayName("declares currentTenantId returning Optional<UUID>")
    void declaresCurrentTenantId() throws Exception {
        Method method = CurrentUserProvider.class.getDeclaredMethod("currentTenantId");
        assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
        assertThat(method.getReturnType()).isEqualTo(Optional.class);
    }
}
