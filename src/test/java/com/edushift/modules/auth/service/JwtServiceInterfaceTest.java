package com.edushift.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke-level interface test for {@link JwtService}. Verifies the contract
 * surface (method names + return types) and the
 * {@link JwtService.JwtClaims} / {@link JwtService.TokenType} record shapes.
 */
class JwtServiceInterfaceTest {

    @Test
    @DisplayName("interface declares the four core methods")
    void surface() throws Exception {
        assertThat(JwtService.class.getMethod("issueAccessToken", User.class, Tenant.class, Set.class)
                .getReturnType()).isEqualTo(String.class);
        assertThat(JwtService.class.getMethod("issueRefreshToken", User.class, Tenant.class)
                .getReturnType()).isEqualTo(String.class);
        assertThat(JwtService.class.getMethod("parseAndValidate", String.class)
                .getReturnType()).isEqualTo(JwtService.JwtClaims.class);
        assertThat(JwtService.class.getMethod("accessTokenTtlSeconds")
                .getReturnType()).isEqualTo(long.class);
    }

    @Test
    @DisplayName("TokenType enum has ACCESS + REFRESH + RESET + MFA + ONBOARDING + IMPERSONATION")
    void tokenTypeEnum() {
        assertThat(JwtService.TokenType.values()).hasSize(6);
        assertThat(JwtService.TokenType.ACCESS).isNotEqualTo(JwtService.TokenType.REFRESH);
        assertThat(JwtService.TokenType.ACCESS).isNotEqualTo(JwtService.TokenType.RESET);
        assertThat(JwtService.TokenType.ACCESS).isNotEqualTo(JwtService.TokenType.MFA);
        assertThat(JwtService.TokenType.REFRESH).isNotEqualTo(JwtService.TokenType.RESET);
        assertThat(JwtService.TokenType.REFRESH).isNotEqualTo(JwtService.TokenType.MFA);
        assertThat(JwtService.TokenType.RESET).isNotEqualTo(JwtService.TokenType.MFA);
        assertThat(JwtService.TokenType.ONBOARDING).isNotEqualTo(JwtService.TokenType.IMPERSONATION);
        assertThat(JwtService.TokenType.ONBOARDING).isNotEqualTo(JwtService.TokenType.ACCESS);
        assertThat(JwtService.TokenType.IMPERSONATION).isNotEqualTo(JwtService.TokenType.ACCESS);
    }

    @Test
    @DisplayName("JwtClaims record accessors")
    void claimsRecord() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var claims = new JwtService.JwtClaims(userId.toString(), tenantId, "acme",
                Set.of("TENANT_ADMIN"), JwtService.TokenType.ACCESS, UUID.randomUUID(),
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(900));
        assertThat(claims.subject()).isEqualTo(userId.toString());
        assertThat(claims.tenantId()).isEqualTo(tenantId);
        assertThat(claims.tenantSlug()).isEqualTo("acme");
        assertThat(claims.roles()).containsExactly("TENANT_ADMIN");
        assertThat(claims.type()).isEqualTo(JwtService.TokenType.ACCESS);
    }

    @Test
    @DisplayName("smoke: a well-formed user + tenant can be assembled for the JWT request")
    void happyInput() {
        var user = new User();
        user.setPublicUuid(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoleSet(Set.of(com.edushift.modules.auth.entity.UserRole.TENANT_ADMIN));
        var tenant = new Tenant();
        tenant.setSlug("acme");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPlan(TenantPlan.PRO);
        assertThat(user.getRoleNames()).contains("TENANT_ADMIN");
        assertThat(tenant.getStatus().canAuthenticate()).isTrue();
    }
}