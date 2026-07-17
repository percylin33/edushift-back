package com.edushift.modules.sessions.template.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "session_templates",
    schema = "edushift",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_session_templates_public_uuid",
                columnNames = "public_uuid"),
        @UniqueConstraint(name = "uk_session_templates_tenant_key",
                columnNames = {"tenant_id", "template_key"})
    },
    indexes = {
        @Index(name = "idx_session_templates_tenant_system",
                columnList = "tenant_id, is_system")
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "templateKey", "name"})
@SQLDelete(sql = "UPDATE edushift.session_templates "
        + "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
        + "WHERE id = ?")
public class SessionTemplate extends TenantAwareEntity {

    @Column(name = "public_uuid", nullable = false, updatable = false,
            unique = true, columnDefinition = "uuid")
    private UUID publicUuid;

    @Column(name = "template_key", nullable = false, length = 100)
    private String templateKey;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> schema;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    private void onPrePersist() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
        if (isSystem == null) {
            isSystem = false;
        }
    }
}
