package com.edushift.modules.academic.section.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Section DTOs")
class DtoTest {

    @Test
    @DisplayName("SectionResponse — constructor + accessors")
    void sectionResponse() {
        var resp = new SectionResponse(UUID.randomUUID(), UUID.randomUUID(), "2026", "ACTIVE",
            UUID.randomUUID(), "1ro Primaria", 1,
            UUID.randomUUID(), "PRIMARIA", "Primaria",
            "A", 30, 1, Instant.now(), Instant.now());
        assertThat(resp.name()).isEqualTo("A");
        assertThat(resp.capacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("SectionListItem — constructor + accessors")
    void sectionListItem() {
        var item = new SectionListItem(UUID.randomUUID(), UUID.randomUUID(), "2026", "ACTIVE",
            UUID.randomUUID(), "1ro Primaria", 1,
            UUID.randomUUID(), "PRIMARIA", "A", 30, 1);
        assertThat(item.name()).isEqualTo("A");
    }

    @Test
    @DisplayName("CreateSectionRequest — constructor + accessors")
    void createSectionRequest() {
        var req = new CreateSectionRequest(UUID.randomUUID(), UUID.randomUUID(), "A", 30, 1);
        assertThat(req.name()).isEqualTo("A");
        assertThat(req.capacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("UpdateSectionRequest — isEmpty checks")
    void updateSectionRequest() {
        var empty = new UpdateSectionRequest(null, null, null);
        assertThat(empty.isEmpty()).isTrue();

        var nonEmpty = new UpdateSectionRequest("B", null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.name()).isEqualTo("B");
    }
}
