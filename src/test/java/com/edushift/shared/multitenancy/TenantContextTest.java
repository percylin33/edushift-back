package com.edushift.shared.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.shared.exception.BusinessException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("set stores the tenant id and current returns it")
    void setAndCurrent() {
        var tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        assertThat(TenantContext.current()).hasValue(tenantId);
    }

    @Test
    @DisplayName("current returns empty when no tenant is set")
    void currentEmptyWhenNotSet() {
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    @DisplayName("currentRequired returns the tenant id when set")
    void currentRequiredReturnsId() {
        var tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        assertThat(TenantContext.currentRequired()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("currentRequired throws BusinessException when no tenant is set")
    void currentRequiredThrowsWhenNotSet() {
        assertThatThrownBy(TenantContext::currentRequired)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tenant context is required for this operation");
    }

    @Test
    @DisplayName("isSet returns true when tenant is set")
    void isSetReturnsTrue() {
        TenantContext.set(UUID.randomUUID());
        assertThat(TenantContext.isSet()).isTrue();
    }

    @Test
    @DisplayName("isSet returns false when no tenant is set")
    void isSetReturnsFalse() {
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("clear removes the stored tenant id")
    void clearRemovesTenant() {
        TenantContext.set(UUID.randomUUID());
        TenantContext.clear();
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("runAs executes the action under the given tenant")
    void runAsExecutesUnderGivenTenant() {
        var tenantId = UUID.randomUUID();
        var result = TenantContext.runAs(tenantId, () -> {
            assertThat(TenantContext.currentRequired()).isEqualTo(tenantId);
            return "done";
        });
        assertThat(result).isEqualTo("done");
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("runAs restores the previous tenant after execution")
    void runAsRestoresPreviousTenant() {
        var prev = UUID.randomUUID();
        TenantContext.set(prev);

        var tmp = UUID.randomUUID();
        TenantContext.runAs(tmp, () -> {
            assertThat(TenantContext.currentRequired()).isEqualTo(tmp);
            return null;
        });

        assertThat(TenantContext.currentRequired()).isEqualTo(prev);
    }

    @Test
    @DisplayName("runAs restores null when there was no previous tenant")
    void runAsRestoresNullWhenNoPrevious() {
        TenantContext.runAs(UUID.randomUUID(), () -> null);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("runAs re-throws exceptions from the action")
    void runAsRethrowsException() {
        var tenantId = UUID.randomUUID();
        assertThatThrownBy(() ->
                TenantContext.runAs(tenantId, () -> { throw new IllegalStateException("fail"); })
        ).isInstanceOf(IllegalStateException.class)
         .hasMessage("fail");
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("tenant context is isolated per thread")
    void threadIsolation() throws Exception {
        var mainTenant = UUID.randomUUID();
        TenantContext.set(mainTenant);

        var otherTenant = UUID.randomUUID();
        var otherResult = CompletableFuture.supplyAsync(() -> {
            TenantContext.set(otherTenant);
            return TenantContext.currentRequired();
        });

        assertThat(otherResult.get()).isEqualTo(otherTenant);
        assertThat(TenantContext.currentRequired()).isEqualTo(mainTenant);
    }
}
