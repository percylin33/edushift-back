package com.edushift.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantAwareEntityTest {

    private static class TestTenantEntity extends TenantAwareEntity {
    }

    @Test
    @DisplayName("tenantId is null by default")
    void tenantIdIsNullByDefault() {
        var entity = new TestTenantEntity();
        assertThat(entity.getTenantId()).isNull();
    }

    @Test
    @DisplayName("setTenantId stores the tenant UUID")
    void setTenantId() {
        var entity = new TestTenantEntity();
        var tenantId = UUID.randomUUID();
        entity.setTenantId(tenantId);
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("extends AuditableEntity")
    void extendsAuditableEntity() {
        assertThat(TenantAwareEntity.class.getSuperclass()).isEqualTo(AuditableEntity.class);
    }
}
