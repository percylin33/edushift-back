package com.edushift.modules.tenants.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates the structural filter applied to tenant slugs before they hit
 * the repository. The goal is to reject hostnames / preview URLs / tunnel
 * ids early so we can warn-log them as client-side misconfiguration
 * instead of just returning 404.
 */
@DisplayName("TenantServiceImpl.isPlausibleTenantSlug")
class TenantServiceImplSlugGuardTest {

    @Test
    void acceptsRealTenants() {
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("demo")).isTrue();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("tecnosur")).isTrue();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("keola-networks")).isTrue();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("a")).isTrue();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("a1b2c3")).isTrue();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("escuela-norte-2026")).isTrue();
    }

    @Test
    void rejectsVercelPreviewUrls() {
        // <8-hex>-<owner>-<repo> pattern from a real Vercel preview hostname.
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("edushift-front-rad5rs5ax-percylin33s-projects")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("edushift-front-abcdef01-team-frontend")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("abc12345-myorg-myrepo")).isFalse();
    }

    @Test
    void rejectsTunnelIds() {
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("3vmchk6t")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("3vmchk6t-8081")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("lima-8080")).isFalse();
    }

    @Test
    void rejectsEmptyAndInvalid() {
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("   ")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug(null)).isFalse();
        // "Demo" normalises to "demo" via toLowerCase; the backend stores
        // slugs case-insensitively, so the validator accepts it.
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("Demo")).isTrue();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("-demo")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("demo-")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("demo--tenant")).isFalse();
        assertThat(TenantServiceImpl.isPlausibleTenantSlug("demo tenant")).isFalse();
    }

    @Test
    void rejectsTooLong() {
        // 25 chars — limit is 24
        String tooLong = "a".repeat(25);
        assertThat(TenantServiceImpl.isPlausibleTenantSlug(tooLong)).isFalse();
    }
}
