package com.edushift.modules.materials.exception;

import com.edushift.modules.materials.error.MaterialsErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a section cannot be found within the bearer's tenant.
 * Used by the {@code POST /sections/{uuid}/materials} endpoint where
 * the existence of the section is a real precondition (not a leak risk).
 */
public class SectionNotFoundException extends NotFoundException {

    public SectionNotFoundException(String sectionPublicUuid) {
        super(MaterialsErrorCodes.SECTION_NOT_FOUND,
              "Section not found: " + sectionPublicUuid);
    }
}
