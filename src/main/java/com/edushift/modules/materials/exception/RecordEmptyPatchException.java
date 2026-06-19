package com.edushift.modules.materials.exception;

import com.edushift.modules.materials.error.MaterialsErrorCodes;
import com.edushift.shared.exception.BusinessException;

/**
 * Thrown when a PATCH body is empty (no fields to update).
 */
public class RecordEmptyPatchException extends BusinessException {

    public RecordEmptyPatchException() {
        super(MaterialsErrorCodes.RECORD_EMPTY_PATCH,
              "PATCH body must contain at least one field to update.");
    }
}
