package com.edushift.modules.tenants.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantNotFoundExceptionTest {

    @Test
    @DisplayName("forSlug builds a message containing the slug")
    void forSlug() {
        var ex = TenantNotFoundException.forSlug("unknown-slug");
        assertThat(ex).isInstanceOf(ResourceNotFoundException.class);
        assertThat(ex.getMessage()).contains("unknown-slug");
    }

    @Test
    @DisplayName("forId builds a message containing the id")
    void forId() {
        var ex = TenantNotFoundException.forId("019e0000-0000-0000-0000-000000000001");
        assertThat(ex.getMessage()).contains("019e0000-0000-0000-0000-000000000001");
    }

    @Test
    @DisplayName("two factories produce distinct messages")
    void distinctMessages() {
        var a = TenantNotFoundException.forSlug("a");
        var b = TenantNotFoundException.forSlug("b");
        assertThat(a.getMessage()).isNotEqualTo(b.getMessage());
    }
}