package com.edushift.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditableEntityTest {

    private static class TestAuditableEntity extends AuditableEntity {
    }

    @Test
    @DisplayName("createdBy and updatedBy are null by default")
    void auditFieldsAreNullByDefault() {
        var entity = new TestAuditableEntity();
        assertThat(entity.getCreatedBy()).isNull();
        assertThat(entity.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("setCreatedBy stores the user UUID")
    void setCreatedBy() {
        var entity = new TestAuditableEntity();
        var userId = UUID.randomUUID();
        entity.setCreatedBy(userId);
        assertThat(entity.getCreatedBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName("setUpdatedBy stores the user UUID")
    void setUpdatedBy() {
        var entity = new TestAuditableEntity();
        var userId = UUID.randomUUID();
        entity.setUpdatedBy(userId);
        assertThat(entity.getUpdatedBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName("extends BaseEntity")
    void extendsBaseEntity() {
        assertThat(AuditableEntity.class.getSuperclass()).isEqualTo(BaseEntity.class);
    }
}
