package com.edushift.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemUsersTest {

    @Test
    @DisplayName("SYSTEM_USER_ID is the expected UUID")
    void systemUserId() {
        assertThat(SystemUsers.SYSTEM_USER_ID).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("ANONYMOUS_USER_ID is the expected UUID")
    void anonymousUserId() {
        assertThat(SystemUsers.ANONYMOUS_USER_ID).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    @DisplayName("constants are distinct")
    void constantsAreDistinct() {
        assertThat(SystemUsers.SYSTEM_USER_ID).isNotEqualTo(SystemUsers.ANONYMOUS_USER_ID);
    }

    @Test
    @DisplayName("private constructor cannot be instantiated via reflection")
    void privateConstructor() throws Exception {
        Constructor<SystemUsers> constructor = SystemUsers.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        constructor.setAccessible(true);
        var instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
