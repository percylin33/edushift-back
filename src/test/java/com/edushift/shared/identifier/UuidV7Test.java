package com.edushift.shared.identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    @DisplayName("create() returns non-null UUID")
    void createReturnsNonNull() {
        var uuid = UuidV7.create();
        assertThat(uuid).isNotNull();
    }

    @Test
    @DisplayName("create() returns UUID version 7")
    void createReturnsVersion7() {
        var uuid = UuidV7.create();
        assertThat(uuid.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("extractTimestamp returns valid Instant for v7 UUID")
    void extractTimestampReturnsInstant() {
        var uuid = UuidV7.create();
        var ts = UuidV7.extractTimestamp(uuid);
        assertThat(ts).isNotNull();
        assertThat(ts).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("extractTimestamp throws IllegalArgumentException for null")
    void extractTimestampThrowsForNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UuidV7.extractTimestamp(null))
                .withMessageContaining("Not a UUIDv7");
    }

    @Test
    @DisplayName("extractTimestamp throws IllegalArgumentException for non-v7 UUID")
    void extractTimestampThrowsForNonV7() {
        var uuid = UUID.randomUUID();
        assertThatThrownBy(() -> UuidV7.extractTimestamp(uuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a UUIDv7: " + uuid);
    }

    @Test
    @DisplayName("1000 generated UUIDs are all unique")
    void generatedUuidsAreUnique() {
        var uuids = new ArrayList<UUID>(1000);
        for (int i = 0; i < 1000; i++) {
            uuids.add(UuidV7.create());
        }
        assertThat(uuids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("1000 generated UUIDs are sortable by timestamp (monotonically increasing)")
    void generatedUuidsAreSortableByTimestamp() {
        var uuids = new ArrayList<UUID>(1000);
        for (int i = 0; i < 1000; i++) {
            uuids.add(UuidV7.create());
        }

        var sorted = new ArrayList<>(uuids);
        sorted.sort(Comparator.naturalOrder());

        assertThat(sorted).containsExactlyElementsOf(uuids);
    }

    @Test
    @DisplayName("extractTimestamp on generated UUIDs yields increasing timestamps")
    void timestampsAreIncreasing() {
        var prev = Instant.MIN;
        for (int i = 0; i < 200; i++) {
            var uuid = UuidV7.create();
            var ts = UuidV7.extractTimestamp(uuid);
            assertThat(ts).isAfterOrEqualTo(prev);
            prev = ts;
        }
    }
}
