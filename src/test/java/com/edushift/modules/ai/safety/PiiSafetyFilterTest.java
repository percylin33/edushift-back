package com.edushift.modules.ai.safety;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PiiSafetyFilter} (Sprint 8 / SEC-8.1).
 */
class PiiSafetyFilterTest {

    private final PiiSafetyFilter filter = new PiiSafetyFilter();

    @Test
    @DisplayName("DNI peruano de 8 digitos se enmascara")
    void dni() {
        String out = filter.mask("Mi DNI es 12345678 y vivo en Lima.");
        assertThat(out).isEqualTo("Mi DNI es XXX-XXX-XX y vivo en Lima.");
    }

    @Test
    @DisplayName("Email se enmascara")
    void email() {
        String out = filter.mask("Escribeme a juan.perez@example.com por favor.");
        assertThat(out).isEqualTo("Escribeme a [email-masked] por favor.");
    }

    @Test
    @DisplayName("Telefono PE 9XX-XXX-XXX se enmascara")
    void phone() {
        String out = filter.mask("Llamame al 987-654-321 hoy.");
        assertThat(out).isEqualTo("Llamame al [phone-masked] hoy.");
    }

    @Test
    @DisplayName("Texto sin PII queda intacto")
    void clean() {
        String out = filter.mask("La revolucion francesa empezo en 1789.");
        assertThat(out).isEqualTo("La revolucion francesa empezo en 1789.");
    }

    @Test
    @DisplayName("null y vacio se manejan sin error")
    void nullAndEmpty() {
        assertThat(filter.mask(null)).isNull();
        assertThat(filter.mask("")).isEqualTo("");
    }
}
