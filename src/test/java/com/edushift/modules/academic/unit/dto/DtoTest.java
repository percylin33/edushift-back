package com.edushift.modules.academic.unit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit DTOs")
class DtoTest {

    @Test
    @DisplayName("UnitResponse — constructor + accessors")
    void unitResponse() {
        var courseRef = new UnitResponse.CourseRef(UUID.randomUUID(), "MAT", "Matemática");
        var resp = new UnitResponse(UUID.randomUUID(), courseRef, "U1", "Desc", 1,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
            true, 5L, Instant.now(), Instant.now());
        assertThat(resp.name()).isEqualTo("U1");
        assertThat(resp.sessionCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("UnitResponse.CourseRef — constructor + accessors")
    void courseRef() {
        var ref = new UnitResponse.CourseRef(UUID.randomUUID(), "MAT", "Mat");
        assertThat(ref.code()).isEqualTo("MAT");
    }

    @Test
    @DisplayName("UnitListItem — constructor + accessors")
    void unitListItem() {
        var item = new UnitListItem(UUID.randomUUID(), "U1", 1,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), true, 3L);
        assertThat(item.isActive()).isTrue();
        assertThat(item.sessionCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("CreateUnitRequest — constructor + accessors")
    void createUnitRequest() {
        var req = new CreateUnitRequest("U1", "Desc", 1,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), true);
        assertThat(req.name()).isEqualTo("U1");
        assertThat(req.displayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("UpdateUnitRequest — isEmpty checks")
    void updateUnitRequest() {
        var empty = new UpdateUnitRequest(null, null, null, null, null);
        assertThat(empty.isEmpty()).isTrue();

        var nonEmpty = new UpdateUnitRequest("Renamed", null, null, null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.name()).isEqualTo("Renamed");
    }

    @Test
    @DisplayName("UnitReorderRequest — with items")
    void unitReorderRequest() {
        var item = new UnitReorderRequest.Item(UUID.randomUUID(), 1);
        var req = new UnitReorderRequest(List.of(item));
        assertThat(req.items()).hasSize(1);
        assertThat(req.items().get(0).displayOrder()).isEqualTo(1);
    }
}
