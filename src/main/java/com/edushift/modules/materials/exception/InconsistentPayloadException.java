package com.edushift.modules.materials.exception;

import com.edushift.modules.materials.error.MaterialsErrorCodes;
import com.edushift.shared.exception.BusinessException;

/**
 * Thrown when a request body's fields do not match the chosen
 * {@link com.edushift.modules.materials.entity.MaterialKind}
 * (Sprint 7a / BE-7a.1). Example: {@code kind=FILE} with a non-null
 * {@code externalUrl}.
 */
public class InconsistentPayloadException extends BusinessException {

    public InconsistentPayloadException(String message) {
        super(MaterialsErrorCodes.INCONSISTENT_PAYLOAD, message);
    }
}
