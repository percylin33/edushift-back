package com.edushift.modules.reports.job;

import com.edushift.modules.reports.entity.ReportJob.Format;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of generated report bytes (Sprint 9 / BE-9.2, MVP).
 *
 * <p>For MVP we keep the bytes here so the FE can download them
 * without an additional file storage round-trip. In production, this
 * will be replaced by uploading to the file_objects table + S3/Firebase
 * Storage (ADR-9.4). Entries expire after 1 hour.</p>
 */
public final class ReportOutputCache {

    record Entry(byte[] bytes, Format format, long expiryEpochMs) {}

    private static final ConcurrentHashMap<UUID, Entry> CACHE = new ConcurrentHashMap<>();
    private static final long TTL_MS = 60 * 60 * 1000L; // 1 hour

    private ReportOutputCache() {}

    public static void put(UUID jobPublicUuid, byte[] bytes, Format format) {
        CACHE.put(jobPublicUuid, new Entry(bytes, format,
                System.currentTimeMillis() + TTL_MS));
    }

    public static Entry get(UUID jobPublicUuid) {
        Entry e = CACHE.get(jobPublicUuid);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiryEpochMs) {
            CACHE.remove(jobPublicUuid);
            return null;
        }
        return e;
    }

    public static void evict(UUID jobPublicUuid) {
        CACHE.remove(jobPublicUuid);
    }
}
