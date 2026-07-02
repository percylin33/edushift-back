package com.edushift.modules.tenants.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantDtoTest {

    @Test
    @DisplayName("BrandingDto stores optional visual fields")
    void brandingDto() {
        var b = new BrandingDto("#1e90ff", "https://cdn/logo.png",
                "https://cdn/favicon.png", "https://cdn/bg.png");
        assertThat(b.primaryColor()).isEqualTo("#1e90ff");
        assertThat(b.logoUrl()).isEqualTo("https://cdn/logo.png");
        assertThat(b.faviconUrl()).isEqualTo("https://cdn/favicon.png");
        assertThat(b.loginBgUrl()).isEqualTo("https://cdn/bg.png");

        var empty = new BrandingDto(null, null, null, null);
        assertThat(empty.primaryColor()).isNull();
    }

    @Test
    @DisplayName("TenantSummary exposes public-safe fields only")
    void tenantSummary() {
        var s = new TenantSummary(UUID.randomUUID(), "Acme", "acme",
                TenantStatus.ACTIVE, new BrandingDto("#000", null, null, null));
        assertThat(s.name()).isEqualTo("Acme");
        assertThat(s.slug()).isEqualTo("acme");
        assertThat(s.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(s.branding().primaryColor()).isEqualTo("#000");
    }

    @Test
    @DisplayName("TenantResponse exposes full authenticated projection")
    void tenantResponse() {
        var id = UUID.randomUUID();
        Map<String, Object> settings = Map.of("language", "es-PE");
        Map<String, Object> flags = Map.of("aiEnabled", true);
        var r = new TenantResponse(id, "Acme", "acme", "acme.edu.pe",
                TenantStatus.ACTIVE, TenantPlan.PRO, Instant.parse("2026-01-01T00:00:00Z"),
                new BrandingDto("#1e90ff", null, null, null),
                settings, flags, 500, 50,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(r.publicUuid()).isEqualTo(id);
        assertThat(r.plan()).isEqualTo(TenantPlan.PRO);
        assertThat(r.maxStudents()).isEqualTo(500);
        assertThat(r.maxTeachers()).isEqualTo(50);
        assertThat(r.settings()).containsEntry("language", "es-PE");
        assertThat(r.featureFlags()).containsEntry("aiEnabled", true);
    }

    @Test
    @DisplayName("UpdateTenantRequest stores nullable partial fields")
    void updateTenantRequest() {
        var req = new UpdateTenantRequest("Acme", null, new BrandingDto("#000", null, null, null),
                null, null, 100, null);
        assertThat(req.name()).isEqualTo("Acme");
        assertThat(req.customDomain()).isNull();
        assertThat(req.maxStudents()).isEqualTo(100);
    }

    @Test
    @DisplayName("RegisterTenantRequest toString masks the password")
    void registerToStringMasks() {
        var req = new RegisterTenantRequest("Acme", "acme", "admin@acme.test",
                "Sup3rSecret!", "Ana", "Diaz");
        String s = req.toString();
        assertThat(s).contains("acme");
        assertThat(s).contains("admin@acme.test");
        assertThat(s).contains("adminFirstName=***");
        assertThat(s).contains("adminPassword=***");
        assertThat(s).doesNotContain("Sup3rSecret!");
    }
}