package com.edushift.modules.materials.exception;

import com.edushift.modules.materials.error.MaterialsErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a material row cannot be found within the bearer's tenant
 * (Sprint 7a / BE-7a.1). Cross-tenant access also resolves here to
 * preserve anti-enumeration (see {@code docs/modules/materials.md} D-MAT-04).
 */
public class MaterialNotFoundException extends NotFoundException {

    public MaterialNotFoundException(String publicUuid) {
        super(MaterialsErrorCodes.MATERIAL_NOT_FOUND,
              "Material not found: " + publicUuid);
    }
}
