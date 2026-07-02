package com.edushift.modules.files.entity;

/**
 * Lifecycle of a {@link FileObject} row (V50 migration, signed-URL flow).
 *
 * <p>See {@code docs/infra/firebase.md} for the end-to-end upload protocol.</p>
 *
 * <h3>State machine</h3>
 * <pre>
 *   [new row]
 *      │
 *      ├── BE-proxied upload (legacy)  → READY  (immediate, bytes already on disk)
 *      │
 *      └── Signed-URL upload           → PENDING (BE mints URL, client PUTs directly)
 *                                          │
 *                                          ├─ POST /confirm (success) → READY
 *                                          ├─ POST /confirm (client-reported fail) → FAILED
 *                                          └─ GC job after 24h (DEBT-7A-1)  → (deleted)
 * </pre>
 */
public enum FileUploadStatus {
    /** Signed URL minted; the bytes have NOT landed in the provider yet. */
    PENDING,
    /** Bytes persisted in the provider; this row is referenceable. */
    READY,
    /** Client reported the PUT failed; housekeeping will GC after 24h. */
    FAILED
}