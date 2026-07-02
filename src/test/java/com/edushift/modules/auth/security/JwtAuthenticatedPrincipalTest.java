package com.edushift.modules.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtAuthenticatedPrincipalTest {

    @Test
    @DisplayName("exposes record accessors for id / tenantId / tenantSlug / email")
    void accessors() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var p = new JwtAuthenticatedPrincipal(userId, tenantId, "acme", "admin@acme.test");
        assertThat(p.getId()).isEqualTo(userId);
        assertThat(p.getTenantId()).isEqualTo(tenantId);
        assertThat(p.getUsername()).isEqualTo("admin@acme.test");
        assertThat(p.id()).isEqualTo(userId);
        assertThat(p.tenantSlug()).isEqualTo("acme");
        assertThat(p.email()).isEqualTo("admin@acme.test");
    }

    @Test
    @DisplayName("toString includes id, tenantSlug, email; does not leak the bearer")
    void toStringShape() {
        var p = new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                "acme", "admin@acme.test");
        String s = p.toString();
        assertThat(s).contains("JwtAuthenticatedPrincipal");
        assertThat(s).contains("tenantSlug=acme");
        assertThat(s).contains("admin@acme.test");
        assertThat(s).startsWith("JwtAuthenticatedPrincipal[id=");
    }
}