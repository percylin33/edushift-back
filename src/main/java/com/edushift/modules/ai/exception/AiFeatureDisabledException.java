package com.edushift.modules.ai.exception;

import org.springframework.http.HttpStatus;

public class AiFeatureDisabledException extends AiModuleException {
    public AiFeatureDisabledException(String feature) {
        super(HttpStatus.FORBIDDEN, "AI_FEATURE_DISABLED",
                "La función de IA " + feature + " no está incluida en el plan del colegio.");
    }
}
