package com.edushift.modules.materials.error;

/**
 * Stable error codes for the LMS materials module (Sprint 7a / BE-7a.1).
 *
 * <p>Source of truth: {@code docs/modules/materials.md} §7.
 */
public final class MaterialsErrorCodes {

    public static final String MATERIAL_NOT_FOUND = "MATERIAL_NOT_FOUND";
    public static final String SECTION_NOT_FOUND = "SECTION_NOT_FOUND";
    public static final String INCONSISTENT_PAYLOAD = "INCONSISTENT_PAYLOAD";
    public static final String RECORD_EMPTY_PATCH = "RECORD_EMPTY_PATCH";

    private MaterialsErrorCodes() {
        // utility class
    }
}
