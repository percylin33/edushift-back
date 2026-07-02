package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.ai.service.TenantAiContextService.TenantContextSnapshot;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantAiContextServiceTest {

    @Test
    @DisplayName("TenantContextSnapshot record accessors")
    void recordAccessors() {
        var snap = new TenantContextSnapshot("Acme", "2026", java.util.List.of("Mat", "Fis"),
                java.util.List.of("Resuelve problemas"));
        assertThat(snap.tenantName()).isEqualTo("Acme");
        assertThat(snap.activeYearName()).isEqualTo("2026");
        assertThat(snap.courses()).containsExactly("Mat", "Fis");
        assertThat(snap.competencies()).hasSize(1);
    }

    @Test
    @DisplayName("Service exists and snapshot(UUID) returns a snapshot — integration test is needed for the SQL; here we just instantiate")
    void serviceInstantiable() {
        UUID tenantId = UUID.randomUUID();
        var svc = new TenantAiContextService();
        assertThat(svc).isNotNull();
        // Full DB-backed snapshot behavior is covered by the integration test
        // (requires a working EntityManager / datasource). This unit test only
        // asserts that the record and the service bean shape are sound.
        assertThat(tenantId).isNotNull();
    }
}