package com.edushift.modules.materials.entity;

/**
 * Discriminator for {@link Material} (Sprint 7a / BE-7a.1).
 *
 * <ul>
 *   <li>{@code FILE} - binary uploaded via the {@code files} module
 *       (see {@code lms_file_objects.file_public_uuid}).</li>
 *   <li>{@code VIDEO_LINK} - external URL (YouTube, Vimeo, etc.); the
 *       material row stores the URL but no bytes are uploaded.</li>
 * </ul>
 *
 * <p>Source of truth: {@code docs/modules/materials.md} D-MAT-03 / D-MAT-04.
 */
public enum MaterialKind {
    FILE,
    VIDEO_LINK
}
