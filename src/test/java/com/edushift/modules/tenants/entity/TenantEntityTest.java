package com.edushift.modules.tenants.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantEntityTest {

    @Test
    @DisplayName("defaults: status=PENDING, plan=TRIAL, empty maps")
    void defaults() {
        var t = new Tenant();
        assertThat(t.getStatus()).isEqualTo(TenantStatus.PENDING);
        assertThat(t.getPlan()).isEqualTo(TenantPlan.TRIAL);
        assertThat(t.getBranding()).isNotNull().isEmpty();
        assertThat(t.getFeatureFlags()).isNotNull().isEmpty();
        assertThat(t.getSettings()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("markDeleted sets deletedAt; restore clears it")
    void lifecycle() {
        var t = new Tenant();
        t.markDeleted();
        assertThat(t.getDeletedAt()).isNotNull();
        assertThat(t.isDeleted()).isTrue();
        t.restore();
        assertThat(t.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("fields round-trip via setters")
    void fields() {
        var t = new Tenant();
        var id = UUID.randomUUID();
        t.setPublicUuid(id);
        t.setName("Acme School");
        t.setSlug("acme");
        t.setCustomDomain("acme.edu.pe");
        t.setMaxStudents(500);
        t.setMaxTeachers(50);
        t.setBranding(new HashMap<>());
        t.setFeatureFlags(new HashMap<>());
        t.setSettings(new HashMap<>());

        assertThat(t.getPublicUuid()).isEqualTo(id);
        assertThat(t.getName()).isEqualTo("Acme School");
        assertThat(t.getSlug()).isEqualTo("acme");
        assertThat(t.getCustomDomain()).isEqualTo("acme.edu.pe");
        assertThat(t.getMaxStudents()).isEqualTo(500);
        assertThat(t.getMaxTeachers()).isEqualTo(50);
    }

    @Nested
    @DisplayName("TenantStatus")
    class Status {

        @Test
        @DisplayName("canAuthenticate only for ACTIVE")
        void canAuthenticate() {
            assertThat(TenantStatus.ACTIVE.canAuthenticate()).isTrue();
            assertThat(TenantStatus.PENDING.canAuthenticate()).isFalse();
            assertThat(TenantStatus.SUSPENDED.canAuthenticate()).isFalse();
            assertThat(TenantStatus.INACTIVE.canAuthenticate()).isFalse();
        }

        @Test
        @DisplayName("enum surface")
        void values() {
            assertThat(TenantStatus.values()).hasSize(4);
        }
    }

    @Nested
    @DisplayName("TenantPlan")
    class Plan {

        @Test
        @DisplayName("enum surface")
        void values() {
            assertThat(TenantPlan.values()).hasSize(4);
            assertThat(TenantPlan.TRIAL).isNotEqualTo(TenantPlan.ENTERPRISE);
        }
    }
}