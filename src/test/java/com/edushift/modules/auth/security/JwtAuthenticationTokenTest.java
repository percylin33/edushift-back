package com.edushift.modules.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class JwtAuthenticationTokenTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private static JwtAuthenticatedPrincipal principal() {
        return new JwtAuthenticatedPrincipal(USER_ID, TENANT_ID, "demo", "admin@demo.test");
    }

    @Test
    @DisplayName("authenticated flag is true when constructed with authorities")
    void authenticated() {
        var token = new JwtAuthenticationToken(principal(), "fake.jwt",
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getCredentials()).isEqualTo("fake.jwt");
        assertThat(token.getPrincipal()).isInstanceOf(JwtAuthenticatedPrincipal.class);
        assertThat(token.getAuthorities()).hasSize(1);
    }

    @Test
    @DisplayName("null principal is rejected")
    void nullPrincipal() {
        assertThatThrownBy(() -> new JwtAuthenticationToken(null, "t",
                List.<GrantedAuthority>of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("principal");
    }

    @Test
    @DisplayName("null authorities is normalised to empty list")
    void nullAuthorities() {
        var token = new JwtAuthenticationToken(principal(), "t", null);
        assertThat(token.getAuthorities()).isEmpty();
        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("getName returns principal id as a string")
    void getName() {
        var token = new JwtAuthenticationToken(principal(), "t",
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
        assertThat(token.getName()).isEqualTo(USER_ID.toString());
    }

    @Test
    @DisplayName("setAuthenticated(true) is refused; setAuthenticated(false) succeeds")
    void setAuthenticatedGuarded() {
        var token = new JwtAuthenticationToken(principal(), "t", List.of());
        assertThatThrownBy(() -> token.setAuthenticated(true))
                .isInstanceOf(IllegalArgumentException.class);
        token.setAuthenticated(false);
        assertThat(token.isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("toString omits the raw bearer token")
    void toStringMasksToken() {
        var token = new JwtAuthenticationToken(principal(), "verysecret.jwt.value",
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
        String repr = token.toString();
        assertThat(repr).doesNotContain("verysecret.jwt.value");
        assertThat(repr).contains(USER_ID.toString());
        assertThat(repr).contains("admin@demo.test");
    }
}