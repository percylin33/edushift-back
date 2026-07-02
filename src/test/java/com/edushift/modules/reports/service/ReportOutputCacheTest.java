package com.edushift.modules.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.job.ReportOutputCache;
import com.edushift.modules.reports.job.ReportOutputCache.Entry;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportOutputCacheTest {

    private final UUID key = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        ReportOutputCache.evict(key);
        // Best-effort: clear the whole cache to keep tests hermetic.
        clearAll();
    }

    private static void clearAll() {
        try {
            Field f = ReportOutputCache.class.getDeclaredField("CACHE");
            f.setAccessible(true);
            ((java.util.Map<?, ?>) f.get(null)).clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("put then get returns the same bytes + format")
    void putAndGet() {
        var bytes = "hello-world".getBytes();
        ReportOutputCache.put(key, bytes, Format.PDF);

        var entry = ReportOutputCache.get(key);

        assertThat(entry).isNotNull();
        assertThat(entry.bytes()).isEqualTo(bytes);
        assertThat(entry.format()).isEqualTo(Format.PDF);
        assertThat(entry.expiryEpochMs()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    @DisplayName("get returns null for unknown UUID")
    void missingKey() {
        assertThat(ReportOutputCache.get(UUID.randomUUID())).isNull();
    }

    @Test
    @DisplayName("evict removes the entry")
    void evict() {
        ReportOutputCache.put(key, "x".getBytes(), Format.CSV);

        assertThat(ReportOutputCache.get(key)).isNotNull();

        ReportOutputCache.evict(key);

        assertThat(ReportOutputCache.get(key)).isNull();
    }

    @Test
    @DisplayName("expired entry is purged and get returns null")
    void expired() throws Exception {
        ReportOutputCache.put(key, "x".getBytes(), Format.XLSX);

        // Force expiry by reflection: rewrite the entry's expiryEpochMs to
        // a moment in the past.
        Field f = ReportOutputCache.class.getDeclaredField("CACHE");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (java.util.Map<UUID, Entry>) f.get(null);
        var stored = map.get(key);
        assertThat(stored).isNotNull();
        map.put(key, new Entry(stored.bytes(), stored.format(),
            System.currentTimeMillis() - 1));

        assertThat(ReportOutputCache.get(key)).isNull();
        // Should also be purged from the underlying map.
        assertThat(map).doesNotContainKey(key);
    }

    @Test
    @DisplayName("put overwrites previous entry with same key")
    void overwrite() {
        ReportOutputCache.put(key, "first".getBytes(), Format.PDF);
        ReportOutputCache.put(key, "second".getBytes(), Format.XLSX);

        var entry = ReportOutputCache.get(key);

        assertThat(entry).isNotNull();
        assertThat(entry.bytes()).isEqualTo("second".getBytes());
        assertThat(entry.format()).isEqualTo(Format.XLSX);
    }
}
