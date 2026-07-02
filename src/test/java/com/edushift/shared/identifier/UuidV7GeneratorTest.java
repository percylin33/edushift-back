package com.edushift.shared.identifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.hibernate.generator.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    @DisplayName("generate() returns new UuidV7 when currentValue is null")
    void generateCreatesNewUuidWhenCurrentValueNull() {
        var generator = new UuidV7Generator();
        var result = generator.generate(null, null, null, EventType.INSERT);
        assertThat(result).isNotNull().isInstanceOf(UUID.class);
        assertThat(((UUID) result).version()).isEqualTo(7);
    }

    @Test
    @DisplayName("generate() returns currentValue when provided")
    void generateReturnsCurrentValueWhenPresent() {
        var generator = new UuidV7Generator();
        var existing = UUID.randomUUID();
        var result = generator.generate(null, null, existing, EventType.INSERT);
        assertThat(result).isEqualTo(existing);
    }

    @Test
    @DisplayName("getEventTypes returns INSERT only")
    void getEventTypesReturnsInsert() {
        var generator = new UuidV7Generator();
        var eventTypes = generator.getEventTypes();
        assertThat(eventTypes).containsExactly(EventType.INSERT);
        assertThat(eventTypes).hasSize(1);
    }
}
